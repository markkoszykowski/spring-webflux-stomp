package io.github.stomp;

import io.github.stomp.StompServer.AckMode;
import io.github.stomp.StompServer.Version;
import io.netty.handler.timeout.ReadTimeoutException;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.NonNull;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaders;
import org.springframework.util.Assert;
import org.springframework.util.CollectionUtils;
import org.springframework.util.MultiValueMap;
import org.springframework.web.reactive.socket.WebSocketHandler;
import org.springframework.web.reactive.socket.WebSocketMessage;
import org.springframework.web.reactive.socket.WebSocketMessage.Type;
import org.springframework.web.reactive.socket.WebSocketSession;
import reactor.core.Disposable;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Sinks;
import reactor.core.scheduler.Scheduler;
import reactor.core.scheduler.Schedulers;
import reactor.util.function.Tuple2;
import reactor.util.function.Tuples;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.stream.Collectors;

@Slf4j
final class StompHandler implements WebSocketHandler {

	static final Scheduler HEARTBEAT_SCHEDULER = Schedulers.parallel();

	final StompServer server;

	StompHandler(final StompServer server) {
		this.server = server;
	}

	static final List<StompServer.Version> SUPPORTED_VERSIONS = List.of(
			StompServer.Version.v1_2,
			StompServer.Version.v1_1,
			StompServer.Version.v1_0
	);

	static final List<String> SUB_PROTOCOLS = SUPPORTED_VERSIONS.stream()
			.map(Version::subProtocol)
			.toList();

	static String versionsToString(final String delimiter) {
		return SUPPORTED_VERSIONS.stream()
				.sorted(Comparator.comparingInt(StompServer.Version::version))
				.map(StompServer.Version::toString)
				.collect(Collectors.joining(delimiter));
	}

	@Override
	public @NonNull List<String> getSubProtocols() {
		return SUB_PROTOCOLS;
	}

	// Caches
	// SessionId -> Subscription -> <ACK Mode, [Ack, ...]>
	final Map<String, Map<String, Tuple2<StompServer.AckMode, Queue<String>>>> ackSubscriptionCache = new ConcurrentHashMap<>();
	// SessionId -> Ack -> StompFrame
	final Map<String, Map<String, StompFrame>> ackFrameCache = new ConcurrentHashMap<>();

	static TriFunction<StompHandler, StompSession, StompFrame, Mono<StompFrame>> handler(final StompCommand command) {
		return switch (command) {
			case StompCommand.STOMP -> StompHandler::handleStomp;
			case StompCommand.CONNECT -> StompHandler::handleConnect;
			case StompCommand.SEND -> StompHandler::handleSend;
			case StompCommand.SUBSCRIBE -> StompHandler::handleSubscribe;
			case StompCommand.UNSUBSCRIBE -> StompHandler::handleUnsubscribe;
			case StompCommand.ACK -> StompHandler::handleAck;
			case StompCommand.NACK -> StompHandler::handleNack;
			case StompCommand.BEGIN -> StompHandler::handleBegin;
			case StompCommand.COMMIT -> StompHandler::handleCommit;
			case StompCommand.ABORT -> StompHandler::handleAbort;
			case StompCommand.DISCONNECT -> StompHandler::handleDisconnect;
			case CONNECTED, RECEIPT, MESSAGE, ERROR -> throw new UnsupportedOperationException("'command' is server STOMP command");
		};
	}

	Function<StompFrame, Mono<StompFrame>> handler(final StompSession session) {
		return inbound -> handler(inbound.command)
				.apply(this, session, inbound)
				.flatMap(outbound -> this.handleError(session, inbound, outbound).thenReturn(outbound));
	}

	Flux<StompFrame> heartbeatReceiver(final StompSession session, final Flux<WebSocketMessage> receives) {
		return receives.filter(StompFrame::isHeartbeat)
				.flatMap(this.doOnEachHeartbeat(session, StompServer::doOnEachInboundHeartbeat))
				.flatMap(_ -> Mono.empty());
	}

	Flux<StompFrame> frameReceiver(final StompSession session, final Flux<WebSocketMessage> receives) {
		return receives.filter(Predicate.not(StompFrame::isHeartbeat))
				.map(StompFrame::from)
				.flatMap(this.doOnEachFrame(session, StompServer::doOnEachInboundFrame))
				.takeUntil(inbound -> inbound.command == StompCommand.DISCONNECT)
				.flatMap(this.handler(session))
				.takeUntil(outbound -> outbound.command == StompCommand.ERROR);
	}

	Flux<WebSocketMessage> heartbeatSender(final StompSession session, final Flux<StompFrame> heartbeats) {
		return Flux.merge(
				session.outgoing.asFlux().flatMap(_ -> this.handleOutgoingHeartbeat(session)),
				session.incoming.asFlux().flatMap(_ -> this.handleIncomingHeartbeat(session))
		);
	}

	Flux<WebSocketMessage> frameSender(final StompSession session, final Flux<StompFrame> frames) {
		return frames.mergeWith(this.server.addWebSocketSources(session).flatMapMany(Flux::merge))
				.doOnNext(this.cacheMessageForAck(session))
				.flatMap(this.doOnEachFrame(session, StompServer::doOnEachOutboundFrame))
				.map(StompFrame.toWebSocketMessage(session.session.bufferFactory()));
	}

	@Override
	public @NonNull Mono<Void> handle(final @NonNull WebSocketSession socketSession) {
		final StompSession session = StompSession.from(socketSession);
		final Flux<WebSocketMessage> receives = session.session.receive().cache(0)
				.doOnNext(this.handleIncoming(session));
		final Flux<StompFrame> heartbeats = this.heartbeatReceiver(session, receives).cache(0);
		final Flux<StompFrame> frames = this.frameReceiver(session, receives).cache(0);
		final Flux<WebSocketMessage> sends = Flux.merge(this.heartbeatSender(session, heartbeats), this.frameSender(session, frames))
				.doOnNext(this.handleOutgoing(session));
		return session.session.send(
						sends.doOnError(ex -> log.error("Error during WebSocket receiving", ex))
								.takeUntilOther(Mono.firstWithSignal(receives.ignoreElements(), heartbeats.ignoreElements(), frames.ignoreElements()))
				)
				.doOnError(ex -> log.error("Error during WebSocket sending", ex))
				.then(Mono.defer(this.doFinally(session)));
	}

	<T> Consumer<T> handleOutgoing(final StompSession session) {
		return _ -> {
			session.sent();
			this.scheduleOutgoingHeartbeat(session);
		};
	}

	<T> Consumer<T> handleIncoming(final StompSession session) {
		return _ -> {
			session.received();
			this.scheduleIncomingHeartbeat(session);
		};
	}

	<T> Function<T, Mono<T>> doOnEachHeartbeat(final StompSession session, final BiFunction<StompServer, StompSession, Mono<Void>> doOnEach) {
		return t -> doOnEach.apply(this.server, session).thenReturn(t);
	}

	Function<StompFrame, Mono<StompFrame>> doOnEachFrame(final StompSession session, final TriFunction<StompServer, StompSession, StompFrame, Mono<Void>> doOnEach) {
		return frame -> doOnEach.apply(this.server, session, frame).thenReturn(frame);
	}

	Supplier<Mono<Void>> doFinally(final StompSession session) {
		return () -> {
			dispose(session.scheduledOutgoing.getAndSet(null));
			dispose(session.scheduledIncoming.getAndSet(null));
			session.outgoing.tryEmitComplete();
			session.incoming.tryEmitComplete();
			return this.server.doFinally(session, this.ackSubscriptionCache.remove(session.id), this.ackFrameCache.remove(session.id));
		};
	}

	Mono<StompFrame> handleProtocolNegotiation(final StompSession session, final StompFrame inbound, final HexFunction<StompServer, StompSession, StompFrame, StompServer.Version, String, StompFrame, Mono<StompFrame>> callback) {
		final String versionsString = inbound.headers.getFirst(StompHeaders.ACCEPT_VERSION);
		final StompServer.Version usingVersion;
		if (versionsString == null) {
			usingVersion = StompServer.Version.v1_0;
		} else {
			final Set<String> versionsSet = Set.of(versionsString.split(","));
			usingVersion = SUPPORTED_VERSIONS.stream().filter(version -> versionsSet.contains(version.version)).findFirst().orElse(null);
		}

		if (usingVersion == null) {
			final MultiValueMap<String, String> headers = CollectionUtils.toMultiValueMap(new HashMap<>());
			headers.add(StompUtils.VERSION, versionsToString(","));

			return Mono.just(StompUtils.makeError(inbound, "unsupported protocol versions", headers, String.format("Supported protocol versions are %s", versionsToString(" "))));
		}

		final String host = inbound.headers.getFirst(StompHeaders.HOST);
		if (host == null) {
			return Mono.just(StompUtils.makeMalformedError(inbound, StompHeaders.HOST));
		}

		final MultiValueMap<String, String> headers = CollectionUtils.toMultiValueMap(new HashMap<>());
		headers.add(StompUtils.VERSION, usingVersion.toString());
		headers.add(StompHeaders.SESSION, session.id);

		return callback.apply(this.server, session, inbound, usingVersion, host, new StompFrame(StompCommand.CONNECTED, headers, null, null));
	}

	Mono<StompFrame> handleStomp(final StompSession session, final StompFrame inbound) {
		return this.handleProtocolNegotiation(session, inbound, StompServer::onStomp);
	}

	Mono<StompFrame> handleConnect(final StompSession session, final StompFrame inbound) {
		return this.handleProtocolNegotiation(session, inbound, StompServer::onConnect);
	}

	static void dispose(final Disposable disposable) {
		if (disposable != null && !disposable.isDisposed()) {
			disposable.dispose();
		}
	}

	static void schedule(final Duration heartbeat, final Sinks.Many<byte[]> sink, final AtomicReference<Disposable> disposable) {
		if (heartbeat.isPositive()) {
			dispose(
					disposable.getAndSet(
							Mono.just(StompFrame.EOL_BYTES)
									.delayElement(heartbeat, HEARTBEAT_SCHEDULER)
									.doOnNext(sink::tryEmitNext)
									.subscribeOn(HEARTBEAT_SCHEDULER)
									.subscribe()
					)
			);
		}
	}

	void scheduleOutgoingHeartbeat(final StompSession session) {
		schedule(session.outgoingHeartbeat, session.outgoing, session.scheduledOutgoing);
	}

	void scheduleIncomingHeartbeat(final StompSession session) {
		schedule(session.incomingHeartbeat, session.incoming, session.scheduledIncoming);
	}

	Mono<WebSocketMessage> handleOutgoingHeartbeat(final StompSession session) {
		if (session.passedOutgoingDeadline()) {
			return Mono.just(new WebSocketMessage(Type.BINARY, session.session.bufferFactory().wrap(StompFrame.EOL_BYTES)))
					.flatMap(this.doOnEachHeartbeat(session, StompServer::doOnEachOutboundHeartbeat));
		}
		return Mono.empty();
	}

	Mono<WebSocketMessage> handleIncomingHeartbeat(final StompSession session) {
		if (session.passedIncomingDeadline()) {
			throw new ReadTimeoutException();
		}
		return Mono.empty();
	}

	Mono<StompFrame> handleSend(final StompSession session, final StompFrame inbound) {
		final String destination = inbound.headers.getFirst(StompHeaders.DESTINATION);
		if (destination == null) {
			return Mono.just(StompUtils.makeMalformedError(inbound, StompHeaders.DESTINATION));
		}
		return this.server.onSend(session, inbound, destination, StompUtils.makeReceipt(inbound));
	}

	Mono<StompFrame> handleSubscribe(final StompSession session, final StompFrame inbound) {
		final String destination = inbound.headers.getFirst(StompHeaders.DESTINATION);
		if (destination == null) {
			return Mono.just(StompUtils.makeMalformedError(inbound, StompHeaders.DESTINATION));
		}

		final String subscriptionId = inbound.headers.getFirst(StompHeaders.ID);
		if (subscriptionId == null) {
			return Mono.just(StompUtils.makeMalformedError(inbound, StompHeaders.ID));
		}

		final StompServer.AckMode ackMode = StompServer.AckMode.from(inbound.headers.getFirst(StompHeaders.ACK));
		if (ackMode == null) {
			return Mono.just(StompUtils.makeError(inbound, "invalid ack mode"));
		}

		final Tuple2<AckMode, Queue<String>> existing = this.ackSubscriptionCache.computeIfAbsent(session.id, _ -> new ConcurrentHashMap<>())
				.putIfAbsent(subscriptionId, Tuples.of(ackMode, new ConcurrentLinkedQueue<>()));
		if (existing != null) {
			return Mono.just(StompUtils.makeError(inbound, "id already in use"));
		}

		return this.server.onSubscribe(session, inbound, destination, subscriptionId, StompUtils.makeReceipt(inbound));
	}

	Mono<StompFrame> handleUnsubscribe(final StompSession session, final StompFrame inbound) {
		final String subscriptionId = inbound.headers.getFirst(StompHeaders.ID);
		if (subscriptionId == null) {
			return Mono.just(StompUtils.makeMalformedError(inbound, StompHeaders.ID));
		}

		final Map<String, Tuple2<StompServer.AckMode, Queue<String>>> subscriptionCache = this.ackSubscriptionCache.get(session.id);
		if (subscriptionCache != null) {
			final Tuple2<StompServer.AckMode, Queue<String>> subscriptionInfo = subscriptionCache.get(subscriptionId);
			if (subscriptionInfo != null) {
				final Map<String, StompFrame> frameCache = this.ackFrameCache.get(session.id);
				if (frameCache != null) {
					subscriptionInfo.getT2().forEach(frameCache::remove);
				}
			}
		}

		return this.server.onUnsubscribe(session, inbound, subscriptionId, StompUtils.makeReceipt(inbound));
	}

	Mono<StompFrame> handleAckOrNack(final StompSession session, final StompFrame inbound, final HeptFunction<StompServer, StompSession, StompFrame, String, String, List<StompFrame>, StompFrame, Mono<StompFrame>> callback) {
		final String ackId = inbound.headers.getFirst(StompHeaders.ID);
		if (ackId == null) {
			return Mono.just(StompUtils.makeMalformedError(inbound, StompHeaders.ID));
		}

		final Map<String, StompFrame> frameCache = this.ackFrameCache.get(session.id);
		if (frameCache == null) {
			return Mono.just(StompUtils.makeError(inbound, "session info not found in cache"));
		}

		final StompFrame frame = frameCache.get(ackId);
		if (frame == null) {
			return Mono.just(StompUtils.makeError(inbound, "frame info not found in cache"));
		}

		final String subscription = frame.headers.getFirst(StompHeaders.SUBSCRIPTION);
		Assert.notNull(subscription, "Sent MESSAGE without subscription");

		final Map<String, Tuple2<StompServer.AckMode, Queue<String>>> subscriptionCache = this.ackSubscriptionCache.get(session.id);
		if (subscriptionCache == null) {
			return Mono.just(StompUtils.makeError(inbound, "session info not found in cache"));
		}

		final Tuple2<StompServer.AckMode, Queue<String>> subscriptionInfo = subscriptionCache.get(subscription);
		if (subscriptionInfo == null) {
			return Mono.just(StompUtils.makeError(inbound, "subscription info not found in cache"));
		}

		final StompServer.AckMode ackMode = subscriptionInfo.getT1();
		final Queue<String> queue = subscriptionInfo.getT2();

		final List<StompFrame> ackOrNackMessages = switch (ackMode) {
			case AUTO -> Collections.emptyList();
			case CLIENT -> {
				synchronized (queue) {
					if (queue.contains(ackId)) {
						final List<StompFrame> messages = new ArrayList<>(Math.max(1, queue.size() / 2));

						String a;
						do {
							a = queue.poll();
							messages.add(frameCache.remove(a));
						} while (!ackId.equals(a));

						yield messages;
					} else {
						yield Collections.emptyList();
					}
				}
			}
			case CLIENT_INDIVIDUAL -> {
				queue.remove(ackId);
				yield Collections.singletonList(frameCache.remove(ackId));
			}
		};

		return callback.apply(this.server, session, inbound, subscription, ackId, ackOrNackMessages, StompUtils.makeReceipt(inbound));
	}

	Mono<StompFrame> handleAck(final StompSession session, final StompFrame inbound) {
		return this.handleAckOrNack(session, inbound, StompServer::onAck);
	}

	Mono<StompFrame> handleNack(final StompSession session, final StompFrame inbound) {
		return this.handleAckOrNack(session, inbound, StompServer::onNack);
	}

	Mono<StompFrame> handleTransactionFrame(final StompSession session, final StompFrame inbound, final QuintFunction<StompServer, StompSession, StompFrame, String, StompFrame, Mono<StompFrame>> callback) {
		final String transaction = inbound.headers.getFirst(StompUtils.TRANSACTION);
		if (transaction == null) {
			return Mono.just(StompUtils.makeMalformedError(inbound, StompUtils.TRANSACTION));
		}
		return callback.apply(this.server, session, inbound, transaction, StompUtils.makeReceipt(inbound));
	}

	Mono<StompFrame> handleBegin(final StompSession session, final StompFrame inbound) {
		return this.handleTransactionFrame(session, inbound, StompServer::onBegin);
	}

	Mono<StompFrame> handleCommit(final StompSession session, final StompFrame inbound) {
		return this.handleTransactionFrame(session, inbound, StompServer::onCommit);
	}

	Mono<StompFrame> handleAbort(final StompSession session, final StompFrame inbound) {
		return this.handleTransactionFrame(session, inbound, StompServer::onAbort);
	}

	Mono<StompFrame> handleDisconnect(final StompSession session, final StompFrame inbound) {
		return this.server.onDisconnect(session, inbound, this.ackSubscriptionCache.remove(session.id), this.ackFrameCache.remove(session.id), StompUtils.makeReceipt(inbound));
	}

	Mono<Void> handleError(final StompSession session, final StompFrame inbound, final StompFrame outbound) {
		if (outbound.command != StompCommand.ERROR) {
			return Mono.empty();
		}
		return this.server.onError(session, inbound, this.ackSubscriptionCache.remove(session.id), this.ackFrameCache.remove(session.id), outbound);
	}

	Consumer<StompFrame> cacheMessageForAck(final StompSession session) {
		return outbound -> {
			if (outbound.command != StompCommand.MESSAGE) {
				return;
			}

			final String subscription = outbound.headers.getFirst(StompHeaders.SUBSCRIPTION);
			Assert.notNull(subscription, "Trying to send MESSAGE without subscription");

			final Map<String, Tuple2<StompServer.AckMode, Queue<String>>> subscriptionCache = this.ackSubscriptionCache.get(session.id);
			if (subscriptionCache == null) {
				return;
			}

			final Tuple2<StompServer.AckMode, Queue<String>> subscriptionInfo = subscriptionCache.get(subscription);
			if (subscriptionInfo == null || subscriptionInfo.getT1() == StompServer.AckMode.AUTO) {
				return;
			}

			final String ackId = UUID.randomUUID().toString();
			subscriptionInfo.getT2().add(ackId);
			this.ackFrameCache.computeIfAbsent(session.id, _ -> new ConcurrentHashMap<>()).put(ackId, outbound);

			outbound.headers.add(StompHeaders.ACK, ackId);
		};
	}

	@FunctionalInterface
	interface TriFunction<T, U, V, R> {
		R apply(T t, U u, V v);
	}

	@FunctionalInterface
	interface QuintFunction<T, U, V, W, X, R> {
		R apply(T t, U u, V v, W w, X x);
	}

	@FunctionalInterface
	interface HexFunction<T, U, V, W, X, Y, R> {
		R apply(T t, U u, V v, W w, X x, Y y);
	}

	@FunctionalInterface
	interface HeptFunction<T, U, V, W, X, Y, Z, R> {
		R apply(T t, U u, V v, W w, X x, Y y, Z z);
	}

}

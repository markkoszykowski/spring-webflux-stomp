package io.github.stomp.server;

import io.github.stomp.StompFrame;
import io.github.stomp.StompServer;
import io.github.stomp.StompSession;
import io.github.stomp.StompUtils;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Component;
import org.springframework.util.Assert;
import reactor.core.Disposable;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Scheduler;
import reactor.core.scheduler.Schedulers;
import reactor.util.function.Tuple2;

import java.time.Duration;
import java.util.Map;
import java.util.Queue;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Component
public class CountingServer implements StompServer {

	private static final Scheduler COUNTING_SCHEDULER = Schedulers.boundedElastic();

	public static final String COUNTING_WEBSOCKET_PATH = "/count";

	private static final long DELAY_MILLIS = 500L;

	private final Map<StompSession, Disposable> subscriptions = new ConcurrentHashMap<>();

	public static StompFrame generateCountMessage(final String destination, final String subscriptionId, final long i) {
		return StompUtils.makeMessage(destination, subscriptionId, 0 < i ? String.valueOf(i) : "Starting count");
	}

	@Override
	public String path() {
		return COUNTING_WEBSOCKET_PATH;
	}

	@Override
	public @NonNull Mono<Void> doOnEachInboundFrame(final @NonNull StompSession session, final @NonNull StompFrame inbound) {
		log.debug("Inbound from {}: {}", session, inbound);
		return StompServer.super.doOnEachInboundFrame(session, inbound);
	}

	@Override
	public @NonNull Mono<Void> doOnEachOutboundFrame(final @NonNull StompSession session, final @NonNull StompFrame outbound) {
		log.debug("Outbound for {}: {}", session, outbound);
		return StompServer.super.doOnEachOutboundFrame(session, outbound);
	}

	@Override
	public @NonNull Mono<Void> doOnEachInboundHeartbeat(final @NonNull StompSession session) {
		log.debug("Inbound heartbeat from {}", session);
		return StompServer.super.doOnEachInboundHeartbeat(session);
	}

	@Override
	public @NonNull Mono<Void> doOnEachOutboundHeartbeat(final @NonNull StompSession session) {
		log.debug("Outbound heartbeat for {}", session);
		return StompServer.super.doOnEachOutboundHeartbeat(session);
	}

	@Override
	public @NonNull Mono<Void> doFinally(final @NonNull StompSession session, final @Nullable Map<String, Tuple2<AckMode, Queue<String>>> subscriptionCache, final @Nullable Map<String, StompFrame> frameCache) {
		final Disposable disposable = this.subscriptions.remove(session);
		if (disposable != null) {
			disposable.dispose();
		}
		return StompServer.super.doFinally(session, subscriptionCache, frameCache);
	}

	@Override
	public @NonNull Mono<StompFrame> onSubscribe(final @NonNull StompSession session, final @NonNull StompFrame inbound, final @NonNull String destination, final @NonNull String subscriptionId, final StompFrame outbound) {
		this.subscriptions.put(
				session,
				Flux.interval(Duration.ofMillis(DELAY_MILLIS), COUNTING_SCHEDULER)
						.doOnNext(i -> Assert.isTrue(session.send(generateCountMessage(destination, subscriptionId, i)), "Failed to send " + i))
						.subscribeOn(COUNTING_SCHEDULER)
						.subscribe()
		);

		return StompServer.super.onSubscribe(session, inbound, destination, subscriptionId, outbound);
	}

	@Override
	public @NonNull Mono<StompFrame> onUnsubscribe(final @NonNull StompSession session, final @NonNull StompFrame inbound, final @NonNull String subscriptionId, final StompFrame outbound) {
		final Disposable disposable = this.subscriptions.remove(session);
		if (disposable != null) {
			disposable.dispose();
		}
		return StompServer.super.onUnsubscribe(session, inbound, subscriptionId, outbound);
	}

}

package io.github.stomp.server;

import io.github.stomp.StompFrame;
import io.github.stomp.StompServer;
import io.github.stomp.StompSession;
import io.github.stomp.StompUtils;
import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.NonNull;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.MimeType;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Sinks;
import reactor.util.function.Tuple2;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Queue;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Component
public class HelloWorldServer implements StompServer {

	public static final String COUNTING_WEBSOCKET_PATH = "/hello-world";

	private final Map<String, Sinks.Many<StompFrame>> sinks = new ConcurrentHashMap<>();

	public static StompFrame generateHelloWorldMessage(final String destination, final String subscriptionId, final MimeType type) {
		return StompUtils.makeMessage(destination, subscriptionId, type, "Hello World!".getBytes(type.getCharset()));
	}

	@Override
	public String path() {
		return COUNTING_WEBSOCKET_PATH;
	}

	@Override
	public @NonNull Mono<List<Flux<StompFrame>>> addWebSocketSources(final @NonNull StompSession session) {
		return Mono.just(
				Collections.singletonList(
						this.sinks.computeIfAbsent(session.id(), _ -> Sinks.many().unicast().onBackpressureBuffer()).asFlux()
				)
		);
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
	public @NonNull Mono<Void> doFinally(final @NonNull StompSession session, final Map<String, Tuple2<AckMode, Queue<String>>> subscriptionCache, final Map<String, StompFrame> frameCache) {
		this.sinks.remove(session.id());
		return StompServer.super.doFinally(session, subscriptionCache, frameCache);
	}

	@Override
	public @NonNull Mono<StompFrame> onSubscribe(final @NonNull StompSession session, final @NonNull StompFrame inbound, final @NonNull String destination, final @NonNull String subscriptionId, final StompFrame outbound) {
		final MimeType type = Optional.ofNullable(inbound.type()).orElse(new MimeType(MediaType.TEXT_PLAIN, StompFrame.DEFAULT_CHARSET));
		final Sinks.Many<StompFrame> sink = this.sinks.get(session.id());
		if (sink != null) {
			sink.tryEmitNext(generateHelloWorldMessage(destination, subscriptionId, type));
		}
		return StompServer.super.onSubscribe(session, inbound, destination, subscriptionId, outbound);
	}

}

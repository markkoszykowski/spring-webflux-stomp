package io.github.stomp.server;

import io.github.stomp.StompFrame;
import io.github.stomp.StompServer;
import io.github.stomp.StompUtils;
import org.jspecify.annotations.NonNull;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.MimeType;
import org.springframework.web.reactive.socket.WebSocketSession;
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
	public @NonNull Mono<List<Flux<StompFrame>>> addWebSocketSources(final @NonNull WebSocketSession session) {
		return Mono.just(
				Collections.singletonList(
						this.sinks.computeIfAbsent(session.getId(), _ -> Sinks.many().unicast().onBackpressureBuffer()).asFlux()
				)
		);
	}

	@Override
	public @NonNull Mono<Void> doFinally(final @NonNull WebSocketSession session, final Map<String, Tuple2<AckMode, Queue<String>>> subscriptionCache, final Map<String, StompFrame> frameCache) {
		this.sinks.remove(session.getId());
		return StompServer.super.doFinally(session, subscriptionCache, frameCache);
	}

	@Override
	public @NonNull Mono<StompFrame> onSubscribe(final @NonNull WebSocketSession session, final @NonNull StompFrame inbound, final @NonNull String destination, final @NonNull String subscriptionId, final StompFrame outbound) {
		final MimeType type = Optional.ofNullable(inbound.type()).orElse(new MimeType(MediaType.TEXT_PLAIN, StompFrame.DEFAULT_CHARSET));
		final Sinks.Many<StompFrame> sink = this.sinks.get(session.getId());
		if (sink != null) {
			sink.tryEmitNext(generateHelloWorldMessage(destination, subscriptionId, type));
		}
		return StompServer.super.onSubscribe(session, inbound, destination, subscriptionId, outbound);
	}

}

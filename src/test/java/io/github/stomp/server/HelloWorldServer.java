package io.github.stomp.server;

import io.github.stomp.StompFrame;
import io.github.stomp.StompServer;
import io.github.stomp.StompUtils;
import org.springframework.stereotype.Component;
import org.springframework.util.MimeType;
import org.springframework.web.reactive.socket.WebSocketSession;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Sinks;
import reactor.util.function.Tuple2;

import java.nio.charset.Charset;
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

	public static StompFrame generateHelloWorldMessage(final String destination, final String subscriptionId, final Charset charset) {
		return StompUtils.makeMessage(destination, subscriptionId, new MimeType("text", "plain", charset), "Hello World!".getBytes(charset));
	}

	@Override
	public String path() {
		return COUNTING_WEBSOCKET_PATH;
	}

	@Override
	public Mono<List<Flux<StompFrame>>> addWebSocketSources(final WebSocketSession session) {
		return Mono.just(
				Collections.singletonList(
						this.sinks.computeIfAbsent(session.getId(), k -> Sinks.many().unicast().onBackpressureBuffer()).asFlux()
				)
		);
	}

	@Override
	public Mono<Void> doFinally(final WebSocketSession session, final Map<String, Tuple2<AckMode, Queue<String>>> subscriptionCache, final Map<String, StompFrame> frameCache) {
		this.sinks.remove(session.getId());
		return StompServer.super.doFinally(session, subscriptionCache, frameCache);
	}

	@Override
	public Mono<StompFrame> onSubscribe(final WebSocketSession session, final StompFrame inbound, final StompFrame outbound, final String destination, final String subscriptionId) {
		final Charset charset = Optional.ofNullable(inbound.bodyCharset()).orElse(StompFrame.DEFAULT_CHARSET);
		final Sinks.Many<StompFrame> sink = this.sinks.get(session.getId());
		if (sink != null) {
			sink.tryEmitNext(generateHelloWorldMessage(destination, subscriptionId, charset));
		}
		return StompServer.super.onSubscribe(session, inbound, outbound, destination, subscriptionId);
	}

}

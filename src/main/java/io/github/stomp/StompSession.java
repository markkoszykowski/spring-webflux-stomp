package io.github.stomp;

import lombok.Getter;
import lombok.experimental.Accessors;
import org.jspecify.annotations.NonNull;
import org.springframework.web.reactive.socket.WebSocketSession;
import reactor.core.Disposable;
import reactor.core.publisher.Sinks;

import java.time.Duration;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;

public final class StompSession {

	@Getter
	@NonNull
	@Accessors(fluent = true)
	final String id;

	@Getter
	@NonNull
	@Accessors(fluent = true)
	final WebSocketSession session;

	@Getter
	@Accessors(fluent = true)
	Duration outgoingHeartbeat = Duration.ZERO;
	@Getter
	@Accessors(fluent = true)
	Duration incomingHeartbeat = Duration.ZERO;

	@Getter
	@Accessors(fluent = true)
	volatile long lastSent;
	@Getter
	@Accessors(fluent = true)
	volatile long lastReceived;

	final AtomicReference<Disposable> scheduledOutgoing = new AtomicReference<>(null);
	final AtomicReference<Disposable> scheduledIncoming = new AtomicReference<>(null);

	final Sinks.Many<byte[]> outgoing = Sinks.many().unicast().onBackpressureBuffer();
	final Sinks.Many<byte[]> incoming = Sinks.many().unicast().onBackpressureBuffer();

	StompSession(final WebSocketSession session) {
		this.id = session.getId();
		this.session = session;
	}

	void sent() {
		this.lastSent = System.currentTimeMillis();
	}

	void received() {
		this.lastReceived = System.currentTimeMillis();
	}

	@Override
	public @NonNull String toString() {
		return Objects.toString(this.session);
	}

}

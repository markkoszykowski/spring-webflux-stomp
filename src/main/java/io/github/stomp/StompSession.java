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
	@NonNull
	@Accessors(fluent = true)
	Duration outgoingHeartbeat = Duration.ZERO;
	long outgoingHeartbeatMilliseconds = 0L;
	@Getter
	@NonNull
	@Accessors(fluent = true)
	Duration incomingHeartbeat = Duration.ZERO;
	long incomingHeartbeatMilliseconds = 0L;

	@Getter
	@Accessors(fluent = true)
	volatile long lastSent = 0L;
	@Getter
	@Accessors(fluent = true)
	volatile long lastReceived = 0L;

	final AtomicReference<Disposable> scheduledOutgoing = new AtomicReference<>(null);
	final AtomicReference<Disposable> scheduledIncoming = new AtomicReference<>(null);

	final Sinks.Many<byte[]> outgoing = Sinks.many().unicast().onBackpressureBuffer();
	final Sinks.Many<byte[]> incoming = Sinks.many().unicast().onBackpressureBuffer();

	StompSession(final WebSocketSession session) {
		this.id = session.getId();
		this.session = session;
	}

	public static StompSession from(final @NonNull WebSocketSession session) {
		return new StompSession(session);
	}


	StompSession outgoingHeartbeatMilliseconds(final long outgoingHeartbeatMilliseconds) {
		this.outgoingHeartbeatMilliseconds = outgoingHeartbeatMilliseconds;
		this.outgoingHeartbeat = Duration.ofMillis(outgoingHeartbeatMilliseconds);
		return this;
	}

	StompSession incomingHeartbeatMilliseconds(final long incomingHeartbeatMilliseconds) {
		this.incomingHeartbeatMilliseconds = incomingHeartbeatMilliseconds;
		this.incomingHeartbeat = Duration.ofMillis(incomingHeartbeatMilliseconds);
		return this;
	}


	void sent() {
		this.lastSent = System.currentTimeMillis();
	}

	void received() {
		this.lastReceived = System.currentTimeMillis();
	}


	boolean passedOutgoingDeadline() {
		return this.lastSent + this.outgoingHeartbeatMilliseconds <= System.currentTimeMillis();
	}

	boolean passedIncomingDeadline() {
		return this.lastReceived + this.incomingHeartbeatMilliseconds < System.currentTimeMillis();
	}


	@Override
	public int hashCode() {
		return Objects.hashCode(this.session);
	}

	@Override
	public boolean equals(final Object o) {
		if (o == this) {
			return true;
		}
		if (o == null || !Objects.equals(this.getClass(), o.getClass())) {
			return false;
		}
		final StompSession that = (StompSession) o;
		return Objects.equals(this.session, that.session);
	}


	@Override
	public @NonNull String toString() {
		return Objects.toString(this.session);
	}

}

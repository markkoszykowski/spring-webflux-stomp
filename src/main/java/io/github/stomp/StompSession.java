package io.github.stomp;

import lombok.Getter;
import lombok.experimental.Accessors;
import org.jspecify.annotations.NonNull;
import org.springframework.util.Assert;
import org.springframework.web.reactive.socket.WebSocketSession;
import reactor.core.Disposable;
import reactor.core.publisher.Sinks;
import reactor.core.publisher.Sinks.Many;

import java.time.Duration;
import java.util.ConcurrentModificationException;
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

	final Many<byte[]> outgoing = Sinks.many().unicast().onBackpressureBuffer();
	final Many<byte[]> incoming = Sinks.many().unicast().onBackpressureBuffer();

	final Many<StompFrame> frames = Sinks.many().unicast().onBackpressureBuffer();

	StompSession(final WebSocketSession socketSession) {
		this.id = socketSession.getId();
		this.session = socketSession;
	}

	public static StompSession from(final @NonNull WebSocketSession socketSession) {
		return new StompSession(socketSession);
	}


	public boolean send(final @NonNull StompFrame frame) {
		Assert.notNull(frame, "'frame' must not be null");
		Assert.isTrue(
				switch (frame.command) {
					case RECEIPT, MESSAGE, ERROR -> true;
					default -> false;
				},
				"Attempting to send invalid server frame"
		);

		return switch (this.frames.tryEmitNext(frame)) {
			case OK -> true;
			case FAIL_TERMINATED -> throw new IllegalStateException("Attempting to send frame to terminated session");
			case FAIL_OVERFLOW -> false;
			case FAIL_CANCELLED -> throw new IllegalStateException("Attempting to send frame to cancelled session");
			case FAIL_NON_SERIALIZED -> throw new ConcurrentModificationException("Attempting to send frame concurrently");
			case FAIL_ZERO_SUBSCRIBER -> throw new IllegalStateException("Attempting to send frame to uninitialized session");
		};
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

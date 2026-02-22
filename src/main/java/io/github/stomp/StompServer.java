package io.github.stomp;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.reactivestreams.Publisher;
import org.springframework.web.reactive.socket.WebSocketSession;
import reactor.core.publisher.Mono;
import reactor.util.function.Tuple2;

import java.util.List;
import java.util.Map;
import java.util.Queue;

public interface StompServer {

	/**
	 * Returns web path of the STOMP server endpoint.
	 *
	 * @return The web path of the STOMP server endpoint.
	 */
	String path();

	enum Version {
		v1_0(1_0, "1.0"),
		v1_1(1_1, "1.1"),
		v1_2(1_2, "1.2");

		final int intVersion;
		final String version;

		Version(final int intVersion, final String version) {
			this.intVersion = intVersion;
			this.version = version;
		}

		public int version() {
			return this.intVersion;
		}

		@Override
		public @NonNull String toString() {
			return this.version;
		}
	}

	enum AckMode {
		AUTO("auto"),
		CLIENT("client"),
		CLIENT_INDIVIDUAL("client-individual");

		final String ackMode;

		AckMode(final String ackMode) {
			this.ackMode = ackMode;
		}

		public static @Nullable AckMode from(final @Nullable String ackMode) {
			if (ackMode == null) {
				return AUTO;
			}
			for (final AckMode mode : AckMode.values()) {
				if (mode.ackMode.equalsIgnoreCase(ackMode)) {
					return mode;
				}
			}
			return null;
		}

		@Override
		public @NonNull String toString() {
			return this.ackMode;
		}
	}

	/**
	 * Adds STOMP frame sources from which frames are forwarded to the websocket client.
	 *
	 * @param session The session to add frame sources to.
	 * @return The list of sources to propagate frames.
	 */
	default @NonNull Mono<? extends @NonNull Iterable<? extends @NonNull Publisher<@NonNull StompFrame>>> addWebSocketSources(final @NonNull WebSocketSession session) {
		return Mono.empty();
	}

	/**
	 * Adds consumer for each inbound STOMP frame.
	 *
	 * @param session The associated websocket session.
	 * @param inbound The inbound STOMP frame.
	 */
	default @NonNull Mono<Void> doOnEachInbound(final @NonNull WebSocketSession session, final @NonNull StompFrame inbound) {
		return Mono.empty();
	}

	/**
	 * Adds consumer for each outbound STOMP frame.
	 *
	 * @param session  The associated websocket session.
	 * @param outbound The outbound STOMP frame.
	 */
	default @NonNull Mono<Void> doOnEachOutbound(final @NonNull WebSocketSession session, final @NonNull StompFrame outbound) {
		return Mono.empty();
	}

	/**
	 * Adds final behavior after websocket closure. The <code>subscriptionCache</code> and <code>frameCache</code> will
	 * only contain valid information upon premature termination of websocket connection by the client.
	 *
	 * @param session           The terminated session.
	 * @param subscriptionCache The map of <code>subscription</code>s mapped to its acknowledgment mode and the queue of unacknowledged outbound <code>ack</code>s expecting an acknowledgment. May be <code>null</code>
	 * @param frameCache        The map of <code>ack</code>s mapped to the unacknowledged outbound frames expecting an acknowledgement. May be <code>null</code>
	 * @see StompServer#onDisconnect(WebSocketSession, StompFrame, Map, Map, StompFrame)
	 * @see StompServer#onError(WebSocketSession, StompFrame, Map, Map, StompFrame)
	 */
	default @NonNull Mono<Void> doFinally(final @NonNull WebSocketSession session, final @Nullable Map<String, Tuple2<AckMode, Queue<String>>> subscriptionCache, final @Nullable Map<String, StompFrame> frameCache) {
		return Mono.empty();
	}

	/**
	 * Adds behaviour upon receiving <code>STOMP</code> frame from client.
	 *
	 * @param session  The associated websocket session.
	 * @param inbound  The inbound client frame.
	 * @param version  The negotiated STOMP protocol version.
	 * @param host     The host requested in the client frame.
	 * @param outbound The potential outbound server frame.
	 * @return The final outbound STOMP frame.
	 */
	default @NonNull Mono<StompFrame> onStomp(final @NonNull WebSocketSession session, final @NonNull StompFrame inbound, final @NonNull Version version, final @NonNull String host, final @NonNull StompFrame outbound) {
		return Mono.just(outbound);
	}

	/**
	 * Adds behaviour upon receiving <code>CONNECT</code> frame from client.
	 *
	 * @param session  The associated websocket session.
	 * @param inbound  The inbound client frame.
	 * @param version  The negotiated STOMP protocol version.
	 * @param host     The host requested in the client frame.
	 * @param outbound The potential outbound server frame.
	 * @return The final outbound STOMP frame.
	 */
	default @NonNull Mono<StompFrame> onConnect(final @NonNull WebSocketSession session, final @NonNull StompFrame inbound, final @NonNull Version version, final @NonNull String host, final @NonNull StompFrame outbound) {
		return Mono.just(outbound);
	}

	/**
	 * Adds behaviour upon receiving <code>SEND</code> frame from client.
	 *
	 * @param session     The associated websocket session.
	 * @param inbound     The inbound client frame.
	 * @param destination The destination of the <code>SEND</code> frame.
	 * @param outbound    The potential outbound server frame. May be <code>null</code>
	 * @return The final outbound STOMP frame.
	 */
	default @NonNull Mono<StompFrame> onSend(final @NonNull WebSocketSession session, final @NonNull StompFrame inbound, final @NonNull String destination, final @Nullable StompFrame outbound) {
		return Mono.justOrEmpty(outbound);
	}

	/**
	 * Adds behaviour upon receiving <code>SUBSCRIBE</code> frame from client.
	 *
	 * @param session        The associated websocket session.
	 * @param inbound        The inbound client frame.
	 * @param destination    The destination of the <code>SUBSCRIBE</code> frame.
	 * @param subscriptionId The subscriptionId of the <code>SUBSCRIBE</code> frame.
	 * @param outbound       The potential outbound server frame. May be <code>null</code>
	 * @return The final outbound STOMP frame.
	 */
	default @NonNull Mono<StompFrame> onSubscribe(final @NonNull WebSocketSession session, final @NonNull StompFrame inbound, final @NonNull String destination, final @NonNull String subscriptionId, final @Nullable StompFrame outbound) {
		return Mono.justOrEmpty(outbound);
	}

	/**
	 * Adds behaviour upon receiving <code>UNSUBSCRIBE</code> frame from client.
	 *
	 * @param session        The associated websocket session.
	 * @param inbound        The inbound client frame.
	 * @param subscriptionId The subscriptionId of the <code>UNSUBSCRIBE</code> frame.
	 * @param outbound       The potential outbound server frame. May be <code>null</code>
	 * @return The final outbound STOMP frame.
	 */
	default @NonNull Mono<StompFrame> onUnsubscribe(final @NonNull WebSocketSession session, final @NonNull StompFrame inbound, final @NonNull String subscriptionId, final @Nullable StompFrame outbound) {
		return Mono.justOrEmpty(outbound);
	}

	/**
	 * Adds behaviour upon receiving <code>ACK</code> frame from client.
	 *
	 * @param session      The associated websocket session.
	 * @param inbound      The inbound client frame.
	 * @param subscription The subscription of the <code>ACK</code> frame.
	 * @param id           The id of the <code>ACK</code> frame.
	 * @param ackMessages  The list of ack-ed messages.
	 * @param outbound     The potential outbound server frame. May be <code>null</code>
	 * @return The final outbound STOMP frame.
	 */
	default @NonNull Mono<StompFrame> onAck(final @NonNull WebSocketSession session, final @NonNull StompFrame inbound, final @NonNull String subscription, final @NonNull String id, final @NonNull List<StompFrame> ackMessages, final @Nullable StompFrame outbound) {
		return Mono.justOrEmpty(outbound);
	}

	/**
	 * Adds behaviour upon receiving <code>NACK</code> frame from client.
	 *
	 * @param session      The associated websocket session.
	 * @param inbound      The inbound client frame.
	 * @param subscription The subscription of the <code>ACK</code> frame.
	 * @param id           The id of the <code>NACK</code> frame.
	 * @param nackMessages The list of nack-ed messages.
	 * @param outbound     The potential outbound server frame. May be <code>null</code>
	 * @return The final outbound STOMP frame.
	 */
	default @NonNull Mono<StompFrame> onNack(final @NonNull WebSocketSession session, final @NonNull StompFrame inbound, final @NonNull String subscription, final @NonNull String id, final @NonNull List<StompFrame> nackMessages, final @Nullable StompFrame outbound) {
		return Mono.justOrEmpty(outbound);
	}

	/**
	 * Adds behaviour upon receiving <code>BEGIN</code> frame from client.
	 *
	 * @param session     The associated websocket session.
	 * @param inbound     The inbound client frame.
	 * @param transaction The transaction of the <code>BEGIN</code> frame.
	 * @param outbound    The potential outbound server frame. May be <code>null</code>
	 * @return The final outbound STOMP frame.
	 */
	default @NonNull Mono<StompFrame> onBegin(final @NonNull WebSocketSession session, final @NonNull StompFrame inbound, final @NonNull String transaction, final @Nullable StompFrame outbound) {
		return Mono.justOrEmpty(outbound);
	}

	/**
	 * Adds behaviour upon receiving <code>COMMIT</code> frame from client.
	 *
	 * @param session     The associated websocket session.
	 * @param inbound     The inbound client frame.
	 * @param transaction The transaction of the <code>COMMIT</code> frame.
	 * @param outbound    The potential outbound server frame. May be <code>null</code>
	 * @return The final outbound STOMP frame.
	 */
	default @NonNull Mono<StompFrame> onCommit(final @NonNull WebSocketSession session, final @NonNull StompFrame inbound, final @NonNull String transaction, final @Nullable StompFrame outbound) {
		return Mono.justOrEmpty(outbound);
	}

	/**
	 * Adds behaviour upon receiving <code>ABORT</code> frame from client.
	 *
	 * @param session     The associated websocket session.
	 * @param inbound     The inbound client frame.
	 * @param transaction The transaction of the <code>ABORT</code> frame.
	 * @param outbound    The potential outbound server frame. May be <code>null</code>
	 * @return The final outbound STOMP frame.
	 */
	default @NonNull Mono<StompFrame> onAbort(final @NonNull WebSocketSession session, final @NonNull StompFrame inbound, final @NonNull String transaction, final @Nullable StompFrame outbound) {
		return Mono.justOrEmpty(outbound);
	}

	/**
	 * Adds behaviour upon receiving <code>DISCONNECT</code> frame from client prior to connection closure. The
	 * <code>subscriptionCache</code> and <code>frameCache</code> will always contain valid information if
	 * unacknowledged frames exist.
	 *
	 * @param session           The associated websocket session.
	 * @param inbound           The inbound client frame.
	 * @param subscriptionCache The map of <code>subscription</code>s mapped to its acknowledgment mode and the queue of unacknowledged outbound <code>ack</code>s expecting an acknowledgment. May be <code>null</code>
	 * @param frameCache        The map of <code>ack</code>s mapped to the unacknowledged outbound frames expecting an acknowledgement. May be <code>null</code>
	 * @param outbound          The potential outbound server frame. May be <code>null</code>
	 * @see StompServer#doFinally(WebSocketSession, Map, Map)
	 * @see StompServer#onError(WebSocketSession, StompFrame, Map, Map, StompFrame)
	 */
	default @NonNull Mono<StompFrame> onDisconnect(final @NonNull WebSocketSession session, final @NonNull StompFrame inbound, final @Nullable Map<String, Tuple2<AckMode, Queue<String>>> subscriptionCache, final @Nullable Map<String, StompFrame> frameCache, final @Nullable StompFrame outbound) {
		return Mono.justOrEmpty(outbound);
	}

	/**
	 * Adds behaviour upon sending <code>ERROR</code> frame to client prior to connection closure. The
	 * <code>subscriptionCache</code> and <code>frameCache</code> will always contain valid information if
	 * unacknowledged frames exist.
	 *
	 * @param session           The associated websocket session.
	 * @param inbound           The inbound client frame.
	 * @param subscriptionCache The map of <code>subscription</code>s mapped to its acknowledgment mode and the queue of unacknowledged outbound <code>ack</code>s expecting an acknowledgment. May be <code>null</code>
	 * @param frameCache        The map of <code>ack</code>s mapped to the unacknowledged outbound frames expecting an acknowledgement. May be <code>null</code>
	 * @param outbound          The potential outbound server frame.
	 * @see StompServer#doFinally(WebSocketSession, Map, Map)
	 * @see StompServer#onDisconnect(WebSocketSession, StompFrame, Map, Map, StompFrame)
	 */
	default @NonNull Mono<Void> onError(final @NonNull WebSocketSession session, final @NonNull StompFrame inbound, final @Nullable Map<String, Tuple2<AckMode, Queue<String>>> subscriptionCache, final @Nullable Map<String, StompFrame> frameCache, final @NonNull StompFrame outbound) {
		return Mono.empty();
	}

}

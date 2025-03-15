package io.github.stomp;

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
		v1_0("1.0"), v1_1("1.1"), v1_2("1.2");

		final float floatVersion;
		final String version;

		Version(final String version) {
			this.version = version;
			this.floatVersion = Float.parseFloat(version);
		}

		public float version() {
			return this.floatVersion;
		}

		@Override
		public String toString() {
			return this.version;
		}
	}

	enum AckMode {
		AUTO("auto"), CLIENT("client"), CLIENT_INDIVIDUAL("client-individual");

		final String ackMode;

		AckMode(final String ackMode) {
			this.ackMode = ackMode;
		}

		public static AckMode from(final String ackMode) {
			if (ackMode == null) {
				return null;
			}
			for (final AckMode mode : AckMode.values()) {
				if (mode.ackMode.equalsIgnoreCase(ackMode)) {
					return mode;
				}
			}
			return null;
		}

		@Override
		public String toString() {
			return this.ackMode;
		}
	}

	/**
	 * Adds STOMP frame sources from which frames are forwarded to the websocket client.
	 *
	 * @param session The session to add frame sources to.
	 * @return The list of sources to propagate frames.
	 */
	default Mono<? extends Iterable<? extends Publisher<StompFrame>>> addWebSocketSources(final WebSocketSession session) {
		return Mono.empty();
	}

	/**
	 * Adds consumer for each inbound STOMP frame.
	 *
	 * @param session The associated websocket session.
	 * @param inbound The inbound STOMP frame.
	 */
	default Mono<Void> doOnEachInbound(final WebSocketSession session, final StompFrame inbound) {
		return Mono.empty();
	}

	/**
	 * Adds consumer for each outbound STOMP frame.
	 *
	 * @param session  The associated websocket session.
	 * @param outbound The outbound STOMP frame.
	 */
	default Mono<Void> doOnEachOutbound(final WebSocketSession session, final StompFrame outbound) {
		return Mono.empty();
	}

	/**
	 * Adds final behavior after websocket closure. The <code>subscriptionCache</code> and <code>frameCache</code> will
	 * only contain valid information upon premature termination of websocket connection by the client.
	 *
	 * @param session           The terminated session.
	 * @param subscriptionCache The map of <code>subscription</code>s mapped to its acknowledgment mode and the queue of unacknowledged outbound <code>ack</code>s expecting an acknowledgment. May be <code>null</code>
	 * @param frameCache        The map of <code>ack</code>s mapped to the unacknowledged outbound frames expecting an acknowledgement. May be <code>null</code>
	 * @see StompServer#onDisconnect(WebSocketSession, StompFrame, StompFrame, Map, Map)
	 * @see StompServer#onError(WebSocketSession, StompFrame, StompFrame, Map, Map)
	 */
	default Mono<Void> doFinally(final WebSocketSession session, final Map<String, Tuple2<AckMode, Queue<String>>> subscriptionCache, final Map<String, StompFrame> frameCache) {
		return Mono.empty();
	}

	/**
	 * Adds behaviour upon receiving <code>STOMP</code> frame from client.
	 *
	 * @param session  The associated websocket session.
	 * @param inbound  The inbound client frame.
	 * @param outbound The potential outbound server frame.
	 * @param version  The negotiated STOMP protocol version.
	 * @param host     The host requested in the client frame. May be <code>null</code>
	 * @return The final outbound STOMP frame.
	 */
	default Mono<StompFrame> onStomp(final WebSocketSession session, final StompFrame inbound, final StompFrame outbound, final Version version, final String host) {
		return Mono.just(outbound);
	}

	/**
	 * Adds behaviour upon receiving <code>CONNECT</code> frame from client.
	 *
	 * @param session  The associated websocket session.
	 * @param inbound  The inbound client frame.
	 * @param outbound The potential outbound server frame.
	 * @param version  The negotiated STOMP protocol version.
	 * @param host     The host requested in the client frame.
	 * @return The final outbound STOMP frame.
	 */
	default Mono<StompFrame> onConnect(final WebSocketSession session, final StompFrame inbound, final StompFrame outbound, final Version version, final String host) {
		return Mono.just(outbound);
	}

	/**
	 * Adds behaviour upon receiving <code>SEND</code> frame from client.
	 *
	 * @param session     The associated websocket session.
	 * @param inbound     The inbound client frame.
	 * @param outbound    The potential outbound server frame. May be <code>null</code>
	 * @param destination The destination of the <code>SEND</code> frame.
	 * @return The final outbound STOMP frame.
	 */
	default Mono<StompFrame> onSend(final WebSocketSession session, final StompFrame inbound, final StompFrame outbound, final String destination) {
		return Mono.justOrEmpty(outbound);
	}

	/**
	 * Adds behaviour upon receiving <code>SUBSCRIBE</code> frame from client.
	 *
	 * @param session        The associated websocket session.
	 * @param inbound        The inbound client frame.
	 * @param outbound       The potential outbound server frame. May be <code>null</code>
	 * @param destination    The destination of the <code>SUBSCRIBE</code> frame.
	 * @param subscriptionId The subscriptionId of the <code>SUBSCRIBE</code> frame.
	 * @return The final outbound STOMP frame.
	 */
	default Mono<StompFrame> onSubscribe(final WebSocketSession session, final StompFrame inbound, final StompFrame outbound, final String destination, final String subscriptionId) {
		return Mono.justOrEmpty(outbound);
	}

	/**
	 * Adds behaviour upon receiving <code>UNSUBSCRIBE</code> frame from client.
	 *
	 * @param session        The associated websocket session.
	 * @param inbound        The inbound client frame.
	 * @param outbound       The potential outbound server frame. May be <code>null</code>
	 * @param subscriptionId The subscriptionId of the <code>UNSUBSCRIBE</code> frame.
	 * @return The final outbound STOMP frame.
	 */
	default Mono<StompFrame> onUnsubscribe(final WebSocketSession session, final StompFrame inbound, final StompFrame outbound, final String subscriptionId) {
		return Mono.justOrEmpty(outbound);
	}

	/**
	 * Adds behaviour upon receiving <code>ACK</code> frame from client.
	 *
	 * @param session      The associated websocket session.
	 * @param inbound      The inbound client frame.
	 * @param outbound     The potential outbound server frame. May be <code>null</code>
	 * @param subscription The subscription of the <code>ACK</code> frame.
	 * @param id           The id of the <code>ACK</code> frame.
	 * @param ackMessages  The list of ack-ed messages.
	 * @return The final outbound STOMP frame.
	 */
	default Mono<StompFrame> onAck(final WebSocketSession session, final StompFrame inbound, final StompFrame outbound, final String subscription, final String id, final List<StompFrame> ackMessages) {
		return Mono.justOrEmpty(outbound);
	}

	/**
	 * Adds behaviour upon receiving <code>NACK</code> frame from client.
	 *
	 * @param session      The associated websocket session.
	 * @param inbound      The inbound client frame.
	 * @param outbound     The potential outbound server frame. May be <code>null</code>
	 * @param subscription The subscription of the <code>ACK</code> frame.
	 * @param id           The id of the <code>NACK</code> frame.
	 * @param nackMessages The list of nack-ed messages.
	 * @return The final outbound STOMP frame.
	 */
	default Mono<StompFrame> onNack(final WebSocketSession session, final StompFrame inbound, final StompFrame outbound, final String subscription, final String id, final List<StompFrame> nackMessages) {
		return Mono.justOrEmpty(outbound);
	}

	/**
	 * Adds behaviour upon receiving <code>BEGIN</code> frame from client.
	 *
	 * @param session     The associated websocket session.
	 * @param inbound     The inbound client frame.
	 * @param outbound    The potential outbound server frame.
	 * @param transaction The transaction of the <code>BEGIN</code> frame.
	 * @return The final outbound STOMP frame.
	 */
	default Mono<StompFrame> onBegin(final WebSocketSession session, final StompFrame inbound, final StompFrame outbound, final String transaction) {
		return Mono.justOrEmpty(outbound);
	}

	/**
	 * Adds behaviour upon receiving <code>COMMIT</code> frame from client.
	 *
	 * @param session     The associated websocket session.
	 * @param inbound     The inbound client frame.
	 * @param outbound    The potential outbound server frame. May be <code>null</code>
	 * @param transaction The transaction of the <code>COMMIT</code> frame.
	 * @return The final outbound STOMP frame.
	 */
	default Mono<StompFrame> onCommit(final WebSocketSession session, final StompFrame inbound, final StompFrame outbound, final String transaction) {
		return Mono.justOrEmpty(outbound);
	}

	/**
	 * Adds behaviour upon receiving <code>ABORT</code> frame from client.
	 *
	 * @param session     The associated websocket session.
	 * @param inbound     The inbound client frame.
	 * @param outbound    The potential outbound server frame. May be <code>null</code>
	 * @param transaction The transaction of the <code>ABORT</code> frame.
	 * @return The final outbound STOMP frame.
	 */
	default Mono<StompFrame> onAbort(final WebSocketSession session, final StompFrame inbound, final StompFrame outbound, final String transaction) {
		return Mono.justOrEmpty(outbound);
	}

	/**
	 * Adds behaviour upon receiving <code>DISCONNECT</code> frame from client prior to connection closure. The
	 * <code>subscriptionCache</code> and <code>frameCache</code> will always contain valid information if
	 * unacknowledged frames exist.
	 *
	 * @param session           The associated websocket session.
	 * @param inbound           The inbound client frame.
	 * @param outbound          The potential outbound server frame. May be <code>null</code>
	 * @param subscriptionCache The map of <code>subscription</code>s mapped to its acknowledgment mode and the queue of unacknowledged outbound <code>ack</code>s expecting an acknowledgment. May be <code>null</code>
	 * @param frameCache        The map of <code>ack</code>s mapped to the unacknowledged outbound frames expecting an acknowledgement. May be <code>null</code>
	 * @see StompServer#doFinally(WebSocketSession, Map, Map)
	 * @see StompServer#onError(WebSocketSession, StompFrame, StompFrame, Map, Map)
	 */
	default Mono<StompFrame> onDisconnect(final WebSocketSession session, final StompFrame inbound, final StompFrame outbound, final Map<String, Tuple2<AckMode, Queue<String>>> subscriptionCache, final Map<String, StompFrame> frameCache) {
		return Mono.justOrEmpty(outbound);
	}

	/**
	 * Adds behaviour upon sending <code>ERROR</code> frame to client prior to connection closure. The
	 * <code>subscriptionCache</code> and <code>frameCache</code> will always contain valid information if
	 * unacknowledged frames exist.
	 *
	 * @param session           The associated websocket session.
	 * @param inbound           The inbound client frame.
	 * @param outbound          The potential outbound server frame.
	 * @param subscriptionCache The map of <code>subscription</code>s mapped to its acknowledgment mode and the queue of unacknowledged outbound <code>ack</code>s expecting an acknowledgment. May be <code>null</code>
	 * @param frameCache        The map of <code>ack</code>s mapped to the unacknowledged outbound frames expecting an acknowledgement. May be <code>null</code>
	 * @see StompServer#doFinally(WebSocketSession, Map, Map)
	 * @see StompServer#onDisconnect(WebSocketSession, StompFrame, StompFrame, Map, Map)
	 */
	default Mono<Void> onError(final WebSocketSession session, final StompFrame inbound, final StompFrame outbound, final Map<String, Tuple2<AckMode, Queue<String>>> subscriptionCache, final Map<String, StompFrame> frameCache) {
		return Mono.empty();
	}

}

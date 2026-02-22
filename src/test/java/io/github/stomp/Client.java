package io.github.stomp;

import org.jspecify.annotations.NonNull;
import org.springframework.messaging.converter.ByteArrayMessageConverter;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompFrameHandler;
import org.springframework.messaging.simp.stomp.StompHeaders;
import org.springframework.messaging.simp.stomp.StompSession;
import org.springframework.messaging.simp.stomp.StompSessionHandler;
import org.springframework.web.socket.client.WebSocketClient;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.messaging.WebSocketStompClient;

import java.lang.reflect.Type;
import java.util.concurrent.ExecutionException;

public class Client implements StompSession, StompSessionHandler {

	private final WebSocketClient client;
	private final WebSocketStompClient stompClient;
	private final StompSession session;

	public Client(final String hostname, final int port, final String path) throws ExecutionException, InterruptedException {
		this.client = new StandardWebSocketClient();
		this.stompClient = new WebSocketStompClient(this.client);
		this.stompClient.setMessageConverter(new ByteArrayMessageConverter());

		this.session = this.stompClient.connectAsync(String.format("ws://%s:%d%s", hostname, port, path), this).get();
	}

	@Override
	public void afterConnected(final @NonNull StompSession session, final @NonNull StompHeaders connectedHeaders) {
	}

	@Override
	public void handleException(final @NonNull StompSession session, final StompCommand command, final @NonNull StompHeaders headers, final byte @NonNull [] payload, final @NonNull Throwable exception) {
	}

	@Override
	public void handleTransportError(final @NonNull StompSession session, final @NonNull Throwable exception) {
	}

	@Override
	public @NonNull Type getPayloadType(final @NonNull StompHeaders headers) {
		return byte[].class;
	}

	@Override
	public void handleFrame(final @NonNull StompHeaders headers, final Object payload) {
	}


	@Override
	public @NonNull String getSessionId() {
		return this.session.getSessionId();
	}

	@Override
	public boolean isConnected() {
		return this.session.isConnected();
	}

	@Override
	public void setAutoReceipt(final boolean enabled) {
		this.session.setAutoReceipt(enabled);
	}

	@Override
	public @NonNull Receiptable send(final @NonNull String destination, final @NonNull Object payload) {
		return this.session.send(destination, payload);
	}

	@Override
	public @NonNull Receiptable send(final @NonNull StompHeaders headers, final @NonNull Object payload) {
		return this.session.send(headers, payload);
	}

	@Override
	public @NonNull Subscription subscribe(final @NonNull String destination, final @NonNull StompFrameHandler handler) {
		return this.session.subscribe(destination, handler);
	}

	@Override
	public @NonNull Subscription subscribe(final @NonNull StompHeaders headers, final @NonNull StompFrameHandler handler) {
		return this.session.subscribe(headers, handler);
	}

	@Override
	public @NonNull Receiptable acknowledge(final @NonNull String messageId, final boolean consumed) {
		return this.session.acknowledge(messageId, consumed);
	}

	@Override
	public @NonNull Receiptable acknowledge(final @NonNull StompHeaders headers, final boolean consumed) {
		return this.session.acknowledge(headers, consumed);
	}

	@Override
	public void disconnect() {
		this.session.disconnect();
	}

	@Override
	public void disconnect(final @NonNull StompHeaders headers) {
		this.session.disconnect(headers);
	}

}

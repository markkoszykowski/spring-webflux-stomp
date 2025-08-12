package io.github.stomp;

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
	public void afterConnected(final StompSession session, final StompHeaders connectedHeaders) {

	}

	@Override
	public void handleException(final StompSession session, final StompCommand command, final StompHeaders headers, final byte[] payload, final Throwable exception) {

	}

	@Override
	public void handleTransportError(final StompSession session, final Throwable exception) {

	}

	@Override
	public Type getPayloadType(final StompHeaders headers) {
		return byte[].class;
	}

	@Override
	public void handleFrame(final StompHeaders headers, final Object payload) {

	}


	@Override
	public String getSessionId() {
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
	public Receiptable send(final String destination, final Object payload) {
		return this.session.send(destination, payload);
	}

	@Override
	public Receiptable send(final StompHeaders headers, final Object payload) {
		return this.session.send(headers, payload);
	}

	@Override
	public Subscription subscribe(final String destination, final StompFrameHandler handler) {
		return this.subscribe(destination, handler);
	}

	@Override
	public Subscription subscribe(final StompHeaders headers, final StompFrameHandler handler) {
		return this.subscribe(headers, handler);
	}

	@Override
	public Receiptable acknowledge(final String messageId, final boolean consumed) {
		return this.acknowledge(messageId, consumed);
	}

	@Override
	public Receiptable acknowledge(final StompHeaders headers, final boolean consumed) {
		return this.session.acknowledge(headers, consumed);
	}

	@Override
	public void disconnect() {
		this.session.disconnect();
	}

	@Override
	public void disconnect(final StompHeaders headers) {
		this.session.disconnect(headers);
	}

}

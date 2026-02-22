package io.github.stomp;

import org.agrona.LangUtil;
import org.jspecify.annotations.NonNull;
import org.springframework.messaging.converter.SimpleMessageConverter;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaders;
import org.springframework.messaging.simp.stomp.StompSession;
import org.springframework.messaging.simp.stomp.StompSession.Receiptable;
import org.springframework.messaging.simp.stomp.StompSession.Subscription;
import org.springframework.messaging.simp.stomp.StompSessionHandler;
import org.springframework.util.Assert;
import org.springframework.web.socket.WebSocketHttpHeaders;
import org.springframework.web.socket.client.WebSocketClient;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.messaging.WebSocketStompClient;
import reactor.util.function.Tuple2;
import reactor.util.function.Tuples;

import java.lang.reflect.Type;
import java.net.InetAddress;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ExecutionException;

public class Client implements AutoCloseable {

	private final WebSocketClient client;
	private final WebSocketStompClient stompClient;
	private final StompSession session;
	private final Handler handler = new Handler();

	private final Map<String, Subscription> subscriptions = new HashMap<>();
	private final MessageQueue<Tuple2<StompHeaders, Optional<byte[]>>> queue = new MessageQueue<>();

	public Client(final InetAddress address, final int port, final String path) throws ExecutionException, InterruptedException {
		this.client = new StandardWebSocketClient();
		this.stompClient = new WebSocketStompClient(this.client);
		this.stompClient.setMessageConverter(new SimpleMessageConverter());

		final StompHeaders headers = new StompHeaders();
		headers.add(StompHeaders.HOST, address.getHostAddress());
		this.session = this.stompClient.connectAsync(String.format("ws://%s:%d%s", address.getHostAddress(), port, path), (WebSocketHttpHeaders) null, headers, this.handler).get();
	}

	public boolean isConnected() {
		return this.session.isConnected();
	}

	public void disconnect() {
		this.session.disconnect();
	}

	public Subscription subscription(final String id) {
		return this.subscriptions.get(id);
	}

	public Subscription subscribe(final String destination, final String id) {
		final StompHeaders headers = new StompHeaders();
		headers.put(StompHeaders.DESTINATION, Collections.singletonList(destination));
		headers.put(StompHeaders.ID, Collections.singletonList(id));

		final Subscription subscription = this.session.subscribe(headers, this.handler);
		this.subscriptions.put(id, subscription);
		return subscription;
	}

	public Receiptable unsubscribe(final String id) {
		final Subscription subscription = this.subscriptions.get(id);
		Assert.notNull(subscription, "subscription '" + id + "' does not exist");
		return subscription.unsubscribe();
	}

	public MessageQueue<Tuple2<StompHeaders, Optional<byte[]>>> queue() {
		return this.queue;
	}

	@Override
	public void close() {
		if (this.session.isConnected()) {
			this.session.disconnect();
		}
		this.stompClient.stop();
	}

	private final class Handler implements StompSessionHandler {
		@Override
		public void afterConnected(final @NonNull StompSession session, final @NonNull StompHeaders connectedHeaders) {
		}

		@Override
		public void handleException(final @NonNull StompSession session, final StompCommand command, final @NonNull StompHeaders headers, final byte @NonNull [] payload, final @NonNull Throwable exception) {
			LangUtil.rethrowUnchecked(exception);
		}

		@Override
		public void handleTransportError(final @NonNull StompSession session, final @NonNull Throwable exception) {
			LangUtil.rethrowUnchecked(exception);
		}

		@Override
		public @NonNull Type getPayloadType(final @NonNull StompHeaders headers) {
			return byte[].class;
		}

		@Override
		public void handleFrame(final @NonNull StompHeaders headers, final Object payload) {
			Client.this.queue.add(Tuples.of(headers, Optional.ofNullable((byte[]) payload)));
		}
	}

}

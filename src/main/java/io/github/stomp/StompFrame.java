package io.github.stomp;

import lombok.Builder;
import lombok.Getter;
import lombok.experimental.Accessors;
import org.agrona.ExpandableDirectByteBuffer;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.DataBufferFactory;
import org.springframework.messaging.Message;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompDecoder;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.NativeMessageHeaderAccessor;
import org.springframework.util.Assert;
import org.springframework.util.CollectionUtils;
import org.springframework.util.MimeType;
import org.springframework.util.MultiValueMap;
import org.springframework.web.reactive.socket.WebSocketMessage;
import org.springframework.web.reactive.socket.WebSocketMessage.Type;

import java.nio.ByteBuffer;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

public final class StompFrame {

	public static final Charset DEFAULT_CHARSET = StandardCharsets.UTF_8;

	static final String NULL = "\0";
	static final String NULL_STRING = "^@";
	static final String EOL = "\n";
	static final String HEADER_SEPARATOR = ":";

	static final byte[] NULL_BYTES = NULL.getBytes(DEFAULT_CHARSET);
	static final byte[] EOL_BYTES = EOL.getBytes(DEFAULT_CHARSET);
	static final byte[] HEADER_SEPARATOR_BYTES = HEADER_SEPARATOR.getBytes(DEFAULT_CHARSET);

	static final ThreadLocal<StompDecoder> DECODER = ThreadLocal.withInitial(StompDecoder::new);

	static final byte[][] COMMAND_BYTES = new byte[StompCommand.values().length][];

	static {
		for (final StompCommand command : StompCommand.values()) {
			COMMAND_BYTES[command.ordinal()] = command.name().getBytes(DEFAULT_CHARSET);
		}
	}

	@Getter
	@NonNull
	@Accessors(fluent = true)
	final StompCommand command;
	final MultiValueMap<String, String> headers;
	MultiValueMap<String, String> immutableHeaders;
	@Getter
	@Nullable
	@Accessors(fluent = true)
	final MimeType type;
	final byte[] body;

	String asString;
	ExpandableDirectByteBuffer asByteBuffer;

	@Builder
	StompFrame(final @NonNull StompCommand command, final @NonNull MultiValueMap<@NonNull String, @Nullable String> headers, final @Nullable MimeType type, final byte @Nullable [] body) {
		Assert.notNull(command, "'command' must not be null");
		Assert.notNull(headers, "'headers' must not be null");

		this.command = command;
		this.headers = headers;
		this.type = type;
		this.body = body;

		this.asString = null;
		this.asByteBuffer = null;
	}

	StompFrame(final WebSocketMessage webSocketMessage) {
		Assert.notNull(webSocketMessage, "'webSocketMessage' must not be null");

		final DataBuffer dataBuffer = webSocketMessage.getPayload();
		final ByteBuffer byteBuffer = ByteBuffer.allocate(dataBuffer.readableByteCount());
		dataBuffer.toByteBuffer(byteBuffer);

		final Message<byte[]> message = DECODER.get().decode(byteBuffer).getFirst();
		final StompHeaderAccessor accessor = StompHeaderAccessor.wrap(message);

		this.command = parseCommand(accessor);
		this.headers = parseHeaders(accessor);
		this.type = parseType(accessor);
		this.body = parseBody(message, accessor);

		this.asString = null;
		this.asByteBuffer = null;
	}

	public static boolean isHeartbeat(final @NonNull WebSocketMessage socketMessage) {
		final DataBuffer dataBuffer = socketMessage.getPayload();
		if (dataBuffer.readableByteCount() != EOL_BYTES.length) {
			return false;
		}
		for (int i = 0; i < EOL_BYTES.length; ++i) {
			if (dataBuffer.getByte(i) != EOL_BYTES[i]) {
				return false;
			}
		}
		return true;
	}

	public static @NonNull StompFrame from(final @NonNull WebSocketMessage socketMessage) {
		return new StompFrame(socketMessage);
	}

	public @NonNull MultiValueMap<@NonNull String, @Nullable String> headers() {
		if (this.immutableHeaders == null) {
			this.immutableHeaders = CollectionUtils.unmodifiableMultiValueMap(this.headers);
		}
		return this.immutableHeaders;
	}

	public byte @Nullable [] body() {
		return this.body == null ? null : this.body.clone();
	}

	public @NonNull String commandString() {
		return this.command.name();
	}

	public StompFrame.@NonNull StompFrameBuilder mutate() {
		return StompFrame.builder()
				.command(this.command)
				.headers(this.headers)
				.type(this.type)
				.body(this.body);
	}


	static StompCommand parseCommand(final StompHeaderAccessor accessor) {
		final StompCommand command = accessor.getCommand();
		Assert.notNull(command, "'command' must not be null");
		return command;
	}

	@SuppressWarnings(value = {"unchecked"})
	static MultiValueMap<String, String> parseHeaders(final StompHeaderAccessor accessor) {
		final Map<String, List<String>> headers = (Map<String, List<String>>) accessor.getHeader(NativeMessageHeaderAccessor.NATIVE_HEADERS);
		return CollectionUtils.toMultiValueMap(headers == null ? Collections.emptyMap() : headers);
	}

	static MimeType parseType(final StompHeaderAccessor accessor) {
		return accessor.getContentType();
	}

	static byte[] parseBody(final Message<byte[]> message, final StompHeaderAccessor accessor) {
		final Integer contentLength = accessor.getContentLength();
		final byte[] temp = message.getPayload();
		if (contentLength == null) {
			return temp;
		} else {
			return contentLength < temp.length ? Arrays.copyOf(temp, contentLength) : temp;
		}
	}


	int capacityGuesstimate() {
		return this.command.name().length() + (64 * this.headers.size()) + (this.body == null ? 0 : this.body.length) + 4;
	}

	@Override
	public @NonNull String toString() {
		if (this.asString != null) {
			return this.asString;
		}

		final StringBuilder sb = new StringBuilder(this.capacityGuesstimate());

		sb.append(this.command.name()).append(EOL);

		for (final Map.Entry<String, List<String>> entry : this.headers.entrySet()) {
			for (final String value : entry.getValue()) {
				sb.append(entry.getKey()).append(HEADER_SEPARATOR);
				if (value != null) {
					sb.append(value);
				}
				sb.append(EOL);
			}
		}

		sb.append(EOL);

		if (this.body != null) {
			if (this.type == null || this.type.getCharset() == null) {
				for (final byte b : this.body) {
					for (int i = Byte.SIZE - 1; 0 <= i; --i) {
						sb.append(((b >>> i) & 0b1) == 0 ? '0' : '1');
					}
				}
			} else {
				sb.append(new String(this.body, this.type.getCharset()));
			}
		}

		sb.append(NULL_STRING);

		return this.asString = sb.toString();
	}

	int putInBuffer(final int index, final byte[] bytes) {
		this.asByteBuffer.putBytes(index, bytes);
		return index + bytes.length;
	}

	public @NonNull ByteBuffer toByteBuffer() {
		if (this.asByteBuffer != null) {
			return this.asByteBuffer.byteBuffer().asReadOnlyBuffer();
		}

		int index = 0;
		this.asByteBuffer = new ExpandableDirectByteBuffer(this.capacityGuesstimate());

		index = this.putInBuffer(index, COMMAND_BYTES[this.command.ordinal()]);
		index = this.putInBuffer(index, EOL_BYTES);

		for (final Map.Entry<String, List<String>> entry : this.headers.entrySet()) {
			final byte[] key = entry.getKey().getBytes(DEFAULT_CHARSET);
			for (final String value : entry.getValue()) {
				index = this.putInBuffer(index, key);
				index = this.putInBuffer(index, HEADER_SEPARATOR_BYTES);
				if (value != null) {
					index = this.putInBuffer(index, value.getBytes(DEFAULT_CHARSET));
				}
				index = this.putInBuffer(index, EOL_BYTES);
			}
		}

		index = this.putInBuffer(index, EOL_BYTES);

		if (this.body != null) {
			index = this.putInBuffer(index, this.body);
		}

		index = this.putInBuffer(index, NULL_BYTES);
		this.asByteBuffer.byteBuffer().clear().position(0).limit(index);

		return this.asByteBuffer.byteBuffer().asReadOnlyBuffer();
	}

	static Function<StompFrame, WebSocketMessage> toWebSocketMessage(final DataBufferFactory factory) {
		return frame -> new WebSocketMessage(Type.BINARY, factory.wrap(frame.toByteBuffer()));
	}

}

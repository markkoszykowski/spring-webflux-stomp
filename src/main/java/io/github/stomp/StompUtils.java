package io.github.stomp;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.springframework.http.MediaType;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaders;
import org.springframework.util.Assert;
import org.springframework.util.CollectionUtils;
import org.springframework.util.MimeType;
import org.springframework.util.MultiValueMap;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class StompUtils {

	private StompUtils() {
	}

	// Header Keys
	public static final String MESSAGE = "message";
	public static final String TRANSACTION = "transaction";
	public static final String VERSION = "version";

	// Header Utils
	static final MimeType DEFAULT_CONTENT_TYPE = new MimeType(MediaType.TEXT_PLAIN, StompFrame.DEFAULT_CHARSET);

	// Helper Functions
	static <K, V> MultiValueMap<K, V> toMultiValueMap(final Map<K, List<V>> map) {
		return map instanceof final MultiValueMap<K, V> multiValueMap ? multiValueMap : map == null ? null : CollectionUtils.toMultiValueMap(map);
	}

	static String contentLength(final byte[] body) {
		return body == null ? "0" : String.valueOf(body.length);
	}

	static String contentType(final MimeType contentType) {
		return contentType == null ? null : contentType.toString();
	}

	// Make Functions
	public static @NonNull StompFrame makeMessage(final @NonNull String destination, final @NonNull String subscription, final @Nullable String body) {
		if (body == null) {
			return makeMessage(destination, subscription, null, null, null);
		} else {
			return makeMessage(destination, subscription, null, DEFAULT_CONTENT_TYPE, body.getBytes(StompFrame.DEFAULT_CHARSET));
		}
	}

	public static @NonNull StompFrame makeMessage(final @NonNull String destination, final @NonNull String subscription, final @Nullable MimeType contentType, final byte @Nullable [] body) {
		return makeMessage(destination, subscription, null, contentType, body);
	}

	public static @NonNull StompFrame makeMessage(final @NonNull String destination, final @NonNull String subscription, final @Nullable Map<String, List<String>> userDefinedHeaders) {
		return makeMessage(destination, subscription, toMultiValueMap(userDefinedHeaders), null, null);
	}

	public static @NonNull StompFrame makeMessage(final @NonNull String destination, final @NonNull String subscription, final @Nullable MultiValueMap<String, String> userDefinedHeaders) {
		return makeMessage(destination, subscription, userDefinedHeaders, null, null);
	}

	public static @NonNull StompFrame makeMessage(final @NonNull String destination, final @NonNull String subscription, final @Nullable Map<String, List<String>> userDefinedHeaders, final @Nullable String body) {
		if (body == null) {
			return makeMessage(destination, subscription, toMultiValueMap(userDefinedHeaders), null, null);
		} else {
			return makeMessage(destination, subscription, toMultiValueMap(userDefinedHeaders), DEFAULT_CONTENT_TYPE, body.getBytes(StompFrame.DEFAULT_CHARSET));
		}
	}

	public static @NonNull StompFrame makeMessage(final @NonNull String destination, final @NonNull String subscription, final @Nullable MultiValueMap<String, String> userDefinedHeaders, final @Nullable String body) {
		if (body == null) {
			return makeMessage(destination, subscription, userDefinedHeaders, null, null);
		} else {
			return makeMessage(destination, subscription, userDefinedHeaders, DEFAULT_CONTENT_TYPE, body.getBytes(StompFrame.DEFAULT_CHARSET));
		}
	}

	public static @NonNull StompFrame makeMessage(final @NonNull String destination, final @NonNull String subscription, final @Nullable Map<String, List<String>> userDefinedHeaders, final @Nullable MimeType contentType, final byte @Nullable [] body) {
		return makeMessage(destination, subscription, toMultiValueMap(userDefinedHeaders), contentType, body);
	}

	public static @NonNull StompFrame makeMessage(final @NonNull String destination, final @NonNull String subscription, final @Nullable MultiValueMap<String, String> userDefinedHeaders, final @Nullable MimeType contentType, final byte @Nullable [] body) {
		Assert.notNull(destination, "'destination' must not be null");
		Assert.notNull(subscription, "'subscription' must not be null");

		final MultiValueMap<String, String> headers = CollectionUtils.toMultiValueMap(new HashMap<>());
		headers.add(StompHeaders.DESTINATION, destination);
		headers.add(StompHeaders.SUBSCRIPTION, subscription);
		headers.add(StompHeaders.MESSAGE_ID, UUID.randomUUID().toString());
		headers.add(StompHeaders.CONTENT_LENGTH, contentLength(body));

		final String contentTypeString = contentType(contentType);
		if (contentTypeString != null) {
			headers.add(StompHeaders.CONTENT_TYPE, contentTypeString);
		}

		if (userDefinedHeaders != null) {
			headers.addAll(userDefinedHeaders);
		}

		return new StompFrame(StompCommand.MESSAGE, headers, contentType, body);
	}

	public static @Nullable StompFrame makeReceipt(final @NonNull StompFrame inbound) {
		Assert.notNull(inbound, "'inbound' must not be null");

		final String receipt = inbound.headers.getFirst(StompHeaders.RECEIPT);
		if (receipt == null) {
			return null;
		}

		final MultiValueMap<String, String> headers = CollectionUtils.toMultiValueMap(new HashMap<>());
		headers.add(StompHeaders.RECEIPT_ID, receipt);

		return new StompFrame(StompCommand.RECEIPT, headers, null, null);
	}

	static StompFrame makeMalformedError(final StompFrame inbound, final String missingHeader) {
		final byte[] body = ("The frame:\n-----\n" + inbound + "\n-----\nDid not contain a " + missingHeader + " header, which is REQUIRED for frame propagation.").getBytes(StompFrame.DEFAULT_CHARSET);
		return makeError(inbound, "malformed frame received", null, DEFAULT_CONTENT_TYPE, body);
	}


	public static @NonNull StompFrame makeError(final @NonNull StompFrame inbound, final @NonNull String errorHeader) {
		return makeError(inbound, errorHeader, null, null, null);
	}

	public static @NonNull StompFrame makeError(final @NonNull StompFrame inbound, final @NonNull String errorHeader, final @Nullable String body) {
		if (body == null) {
			return makeError(inbound, errorHeader, null, null, null);
		} else {
			return makeError(inbound, errorHeader, null, DEFAULT_CONTENT_TYPE, body.getBytes(StompFrame.DEFAULT_CHARSET));
		}
	}

	public static @NonNull StompFrame makeError(final @NonNull StompFrame inbound, final @NonNull String errorHeader, final @Nullable MimeType contentType, final byte @Nullable [] body) {
		return makeError(inbound, errorHeader, null, contentType, body);
	}

	public static @NonNull StompFrame makeError(final @NonNull StompFrame inbound, final @NonNull String errorHeader, final @Nullable Map<String, List<String>> userDefinedHeaders) {
		return makeError(inbound, errorHeader, toMultiValueMap(userDefinedHeaders), null, null);
	}

	public static @NonNull StompFrame makeError(final @NonNull StompFrame inbound, final @NonNull String errorHeader, final @Nullable MultiValueMap<String, String> userDefinedHeaders) {
		return makeError(inbound, errorHeader, userDefinedHeaders, null, null);
	}

	public static @NonNull StompFrame makeError(final @NonNull StompFrame inbound, final @NonNull String errorHeader, final @Nullable Map<String, List<String>> userDefinedHeaders, final @Nullable String body) {
		if (body == null) {
			return makeError(inbound, errorHeader, toMultiValueMap(userDefinedHeaders), null, null);
		} else {
			return makeError(inbound, errorHeader, toMultiValueMap(userDefinedHeaders), DEFAULT_CONTENT_TYPE, body.getBytes(StompFrame.DEFAULT_CHARSET));
		}
	}

	public static @NonNull StompFrame makeError(final @NonNull StompFrame inbound, final @NonNull String errorHeader, final @Nullable MultiValueMap<String, String> userDefinedHeaders, final @Nullable String body) {
		if (body == null) {
			return makeError(inbound, errorHeader, userDefinedHeaders, null, null);
		} else {
			return makeError(inbound, errorHeader, userDefinedHeaders, DEFAULT_CONTENT_TYPE, body.getBytes(StompFrame.DEFAULT_CHARSET));
		}
	}

	public static @NonNull StompFrame makeError(final @NonNull StompFrame inbound, final @NonNull String errorHeader, final @Nullable Map<String, List<String>> userDefinedHeaders, final @Nullable MimeType contentType, final byte @Nullable [] body) {
		return makeError(inbound, errorHeader, toMultiValueMap(userDefinedHeaders), contentType, body);
	}

	public static @NonNull StompFrame makeError(final @NonNull StompFrame inbound, final @NonNull String errorHeader, final @Nullable MultiValueMap<String, String> userDefinedHeaders, final @Nullable MimeType contentType, final byte @Nullable [] body) {
		Assert.notNull(inbound, "'inbound' must not be null");
		Assert.notNull(errorHeader, "'errorHeader' must not be null");

		final MultiValueMap<String, String> headers = CollectionUtils.toMultiValueMap(new HashMap<>());
		headers.add(MESSAGE, errorHeader);
		headers.add(StompHeaders.CONTENT_LENGTH, contentLength(body));

		final String contentTypeString = contentType(contentType);
		if (contentTypeString != null) {
			headers.add(StompHeaders.CONTENT_TYPE, contentTypeString);
		}

		final String receipt = inbound.headers.getFirst(StompHeaders.RECEIPT);
		if (receipt != null) {
			headers.add(StompHeaders.RECEIPT_ID, receipt);
		}

		if (userDefinedHeaders != null) {
			headers.addAll(userDefinedHeaders);
		}

		return new StompFrame(StompCommand.ERROR, headers, contentType, body);
	}

}

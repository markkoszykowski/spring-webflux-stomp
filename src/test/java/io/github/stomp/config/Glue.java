package io.github.stomp.config;

import io.cucumber.datatable.DataTable;
import io.cucumber.java.ParameterType;
import io.github.stomp.StompFrame;
import io.github.stomp.server.Endpoint;
import org.agrona.LangUtil;
import org.springframework.messaging.simp.stomp.StompHeaders;
import org.springframework.util.MimeType;
import reactor.util.function.Tuple2;
import tools.jackson.dataformat.javaprop.JavaPropsMapper;
import tools.jackson.dataformat.javaprop.JavaPropsSchema;

import java.io.IOException;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@SuppressWarnings(value = {"unused"})
public class Glue {

	static final JavaPropsSchema SCHEMA = new JavaPropsSchema().withFirstArrayOffset(0);
	static final JavaPropsMapper MAPPER = JavaPropsMapper.shared();

	@ParameterType(value = "counting|hello world")
	public Endpoint endpoint(final String endpoint) {
		return switch (endpoint) {
			case "counting" -> Endpoint.COUNTING;
			case "hello world" -> Endpoint.HELLO_WORLD;
			default -> throw new IllegalArgumentException();
		};
	}

	@ParameterType(value = "successfully|unsuccessfully")
	public boolean success(final String endpoint) {
		return switch (endpoint) {
			case "successfully" -> true;
			case "unsuccessfully" -> false;
			default -> throw new IllegalArgumentException();
		};
	}

	@ParameterType(value = "connected|disconnected")
	public boolean connected(final String endpoint) {
		return switch (endpoint) {
			case "connected" -> true;
			case "disconnected" -> false;
			default -> throw new IllegalArgumentException();
		};
	}

	public static String payload(final StompHeaders headers, final byte[] payload) {
		return new String(
				payload,
				Optional.ofNullable(headers.getContentType())
						.map(MimeType::getCharset)
						.orElse(StompFrame.DEFAULT_CHARSET)
		);
	}

	public static <K, V> Map.Entry<K, ?> flatten(final Map.Entry<K, ? extends Collection<V>> entry) {
		if (entry == null) {
			return null;
		}
		final Collection<? extends V> values = entry.getValue();
		if (values.isEmpty()) {
			return null;
		}
		if (values.size() == 1) {
			return Map.entry(entry.getKey(), values.iterator().next());
		}
		return entry;
	}

	public static <K, V> Map<K, Object> flatten(final Map<K, ? extends Collection<V>> multivaluemap) {
		return multivaluemap.entrySet().stream()
				.map(Glue::flatten)
				.collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue, (x, y) -> y, LinkedHashMap::new));
	}

	public static Map<String, String> toMap(final StompHeaders headers, final byte[] payload) {
		try {
			return MAPPER.writeValueAsMap(
					payload == null ?
							Map.of("Headers", flatten(headers)) :
							Map.of(
									"Headers", flatten(headers),
									"Payload", payload(headers, payload)
							),
					SCHEMA
			);
		} catch (final IOException ex) {
			LangUtil.rethrowUnchecked(ex);
		}
		return Collections.emptyMap();
	}

	public static Map<String, String> toMap(final Tuple2<StompHeaders, Optional<byte[]>> frame) {
		return toMap(frame.getT1(), frame.getT2().orElse(null));
	}

	public static List<String> toRow(final List<String> columns, final Map<String, String> row) {
		return columns.stream()
				.map(row::get)
				.toList();
	}

	public static DataTable toDataTable(final List<String> columns, final List<Map<String, String>> rows) {
		final List<List<String>> data = rows.stream()
				.map(row -> toRow(columns, row))
				.toList();
		return DataTable.create(Stream.concat(Stream.of(List.copyOf(columns)), data.stream()).toList());
	}

	public static DataTable toDataTable(final List<Map<String, String>> rows) {
		final List<String> columns = rows.stream()
				.map(Map::keySet)
				.flatMap(Set::stream)
				.distinct()
				.toList();
		return toDataTable(columns, rows);
	}

}

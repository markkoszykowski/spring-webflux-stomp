package io.github.stomp.config;

import io.cucumber.java.ParameterType;
import io.github.stomp.server.Endpoint;

@SuppressWarnings(value = {"unused"})
public class Glue {

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

}

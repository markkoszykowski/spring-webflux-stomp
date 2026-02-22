package io.github.stomp.config;

import io.cucumber.java.en.When;
import io.github.stomp.server.Endpoint;
import io.github.stomp.Client;
import io.github.stomp.Server;
import org.agrona.concurrent.IdleStrategy;
import org.agrona.concurrent.SleepingMillisIdleStrategy;
import org.junit.jupiter.api.Assertions;
import org.springframework.core.env.Environment;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutionException;

@SuppressWarnings(value = {"unused"})
public class StepsDefinition {

	private final Environment environment;

	private final Server server = new Server();

	private final Map<String, Client> clients = new HashMap<>();

	public StepsDefinition(final Environment environment) {
		this.environment = environment;
	}

	@When(value = "the STOMP server is up")
	public void startServer() {
		this.server.start();
	}

	@When(value = "the STOMP server is down")
	public void stopServer() {
		this.server.stop();
	}

	@When(value = "the sample STOMP server is up")
	public void startServerAndBlock() {
		this.server.start();
		final IdleStrategy idleStrategy = new SleepingMillisIdleStrategy(1_000L);
		while (true) {
			idleStrategy.idle();
		}
	}

	@When(value = "client {string} connects to the {endpoint} server {success}")
	public void clientConnect(final String clientId, final Endpoint endpoint, final boolean success) {
		boolean successful = true;
		try {
			this.clients.computeIfAbsent(clientId, k -> {
				try {
					return new Client("127.0.0.1", this.environment.getRequiredProperty("local.server.port", Integer.class), endpoint.path());
				} catch (final ExecutionException | InterruptedException ex) {
					throw new RuntimeException();
				}
			});
		} catch (final RuntimeException ex) {
			successful = false;
		}
		Assertions.assertEquals(success, successful);
	}

}

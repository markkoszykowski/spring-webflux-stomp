package io.github.stomp.config;

import io.cucumber.datatable.DataTable;
import io.cucumber.datatable.DataTableDiff;
import io.cucumber.datatable.TableDiffer;
import io.cucumber.java.en.And;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import io.github.stomp.Client;
import io.github.stomp.Server;
import io.github.stomp.server.Endpoint;
import org.agrona.CloseHelper;
import org.agrona.concurrent.IdleStrategy;
import org.agrona.concurrent.SleepingMillisIdleStrategy;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.springframework.boot.web.server.autoconfigure.ServerProperties;
import org.springframework.messaging.simp.stomp.StompHeaders;
import reactor.util.function.Tuple2;

import java.net.InetAddress;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Queue;
import java.util.concurrent.ExecutionException;

@SuppressWarnings(value = {"unused"})
public class StepsDefinition implements AutoCloseable {

	private final Server server = new Server();
	private final Map<String, Client> clients = new HashMap<>();

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

	private void nonExistingClient(final String clientId) {
		Assertions.assertFalse(this.clients.containsKey(clientId), "client '" + clientId + "' already exists");
	}

	private Client existingClient(final String clientId) {
		final Client client = this.clients.get(clientId);
		Assertions.assertNotNull(client, "client '" + clientId + "' does not exist");
		return client;
	}

	@When(value = "client {string} connects to the {endpoint} server {success}")
	public void clientConnect(final String clientId, final Endpoint endpoint, final boolean success) {
		this.nonExistingClient(clientId);

		boolean successful = true;
		try {
			final ServerProperties properties = this.server.properties();
			final Client client = new Client(
					Optional.ofNullable(properties.getAddress()).orElse(InetAddress.getLoopbackAddress()),
					Optional.ofNullable(properties.getPort()).orElse(8080),
					endpoint.path()
			);
			this.clients.put(clientId, client);
		} catch (final ExecutionException | InterruptedException | RuntimeException ex) {
			successful = false;
		}
		Assertions.assertEquals(success, successful);
	}

	@And(value = "client {string} disconnects")
	public void clientDisconnects(final String clientId) {
		final Client client = this.existingClient(clientId);
		client.disconnect();
	}

	@Then(value = "client {string} is {connected}")
	public void clientDisconnected(final String clientId, final boolean connected) {
		final Client client = this.existingClient(clientId);
		Awaitility.await()
				.untilAsserted(() -> Assertions.assertEquals(connected, client.isConnected()));
	}

	@And(value = "client {string} subscribes to {string} with id {string}")
	public void clientSubscribes(final String clientId, final String destination, final String id) {
		final Client client = this.existingClient(clientId);
		Assertions.assertNotNull(client.subscribe(destination, id));
	}

	@And(value = "client {string} unsubscribes with id {string}")
	public void clientUnsubscribes(final String clientId, final String id) {
		final Client client = this.existingClient(clientId);
		Assertions.assertNotNull(client.unsubscribe(id));
	}

	private static DataTable toDataTable(final Collection<Tuple2<StompHeaders, Optional<byte[]>>> frames) {
		return Glue.toDataTable(frames.stream().map(Glue::toMap).toList());
	}

	private static DataTable toDataTable(final List<String> columns, final Collection<Tuple2<StompHeaders, Optional<byte[]>>> frames) {
		return Glue.toDataTable(columns, frames.stream().map(Glue::toMap).toList());
	}

	@Then(value = "client {string} receives:")
	public void clientReceives(final String clientId, final DataTable expected) {
		final Client client = this.existingClient(clientId);

		final Queue<Tuple2<StompHeaders, Optional<byte[]>>> received = client.queue().unviewed();
		try {
			Awaitility.await()
					.untilAsserted(
							() -> {
								final DataTable actual = toDataTable(expected.row(0), received);
								final TableDiffer differ = new TableDiffer(actual, expected);
								final DataTableDiff diff = differ.calculateDiffs();
								Assertions.assertTrue(
										diff.isEmpty(),
										() ->
												"Diff: " + System.lineSeparator() + diff + System.lineSeparator() +
														"Received: " + System.lineSeparator() + toDataTable(received) + System.lineSeparator()
								);
							}
					);
		} finally {
			received.clear();
		}
	}

	@Override
	@AfterEach
	public void close() {
		CloseHelper.quietCloseAll(this.clients.values());
		CloseHelper.quietClose(this.server);
	}

}

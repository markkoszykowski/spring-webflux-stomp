package io.github.stomp;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.web.server.autoconfigure.ServerProperties;
import org.springframework.context.ApplicationContext;
import org.springframework.util.Assert;

@SpringBootApplication
public class Server implements AutoCloseable {

	private ApplicationContext context = null;

	public ServerProperties properties() {
		Assert.notNull(this.context, "server is not started");
		return this.context.getBean(ServerProperties.class);
	}

	public Server start() {
		if (this.context == null) {
			this.context = SpringApplication.run(Server.class);
		}
		return this;
	}

	public Server stop() {
		if (this.context != null) {
			if (SpringApplication.exit(this.context) != 0) {
				throw new RuntimeException("Server did not exit successfully");
			}
			this.context = null;
		}
		return this;
	}

	@Override
	public void close() {
		this.stop();
	}

}

package io.jstach.rainbowgum.rabbitmq;

import java.io.IOException;
import java.net.URI;

import io.jstach.rainbowgum.LogConfig;
import io.jstach.rainbowgum.LogOutput;
import io.jstach.rainbowgum.LogOutputProvider;
import io.jstach.rainbowgum.LogProperties;
import io.jstach.rainbowgum.ServiceRegistry;
import io.jstach.rainbowgum.spi.RainbowGumServiceProvider;
import io.jstach.svc.ServiceProvider;

/**
 * RabbitMQ initializer to register output provider with scheme {@value #SCHEME}.
 */
@ServiceProvider(RainbowGumServiceProvider.class)
public class RabbitMQInitializer implements RainbowGumServiceProvider.Initializer {

	/**
	 * Default constructor for service loader.
	 */
	public RabbitMQInitializer() {
	}

	final static String SCHEME = "amqp";

	@Override
	public void initialize(ServiceRegistry registry, LogConfig config) {
		config.outputRegistry().put(SCHEME, RabbitMQOutputProvider.INSTANCE);
	}

	private enum RabbitMQOutputProvider implements LogOutputProvider {

		INSTANCE;

		@Override
		public LogOutput output(URI uri, String name, LogProperties properties) throws IOException {
			name = name.equals("") ? "rabbitmq" : name;
			RabbitMQOutputBuilder b = new RabbitMQOutputBuilder(name);
			b.uri(uri);
			b.fromProperties(properties);
			return b.build();
		}

	}

}

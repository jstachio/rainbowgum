package io.jstach.rainbowgum.rabbitmq;

import java.io.IOException;
import java.net.URI;

import io.jstach.rainbowgum.LogConfig;
import io.jstach.rainbowgum.LogOutput;
import io.jstach.rainbowgum.LogOutput.OutputProvider;
import io.jstach.rainbowgum.LogProperties;
import io.jstach.rainbowgum.spi.RainbowGumServiceProvider;
import io.jstach.svc.ServiceProvider;

/**
 * RabbitMQ initializer to register output provider with scheme
 * {@value RabbitMQOutput#URI_SCHEME}.
 */
@ServiceProvider(RainbowGumServiceProvider.class)
public class RabbitMQInitializer implements RainbowGumServiceProvider.Configurator {

	/**
	 * Default constructor for service loader.
	 */
	public RabbitMQInitializer() {
	}

	@Override
	public boolean configure(LogConfig config) {
		config.outputRegistry().register(RabbitMQOutput.URI_SCHEME, RabbitMQOutputProvider.INSTANCE);
		return true;
	}

	private enum RabbitMQOutputProvider implements OutputProvider {

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

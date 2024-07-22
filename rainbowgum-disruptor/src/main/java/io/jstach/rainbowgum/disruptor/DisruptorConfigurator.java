package io.jstach.rainbowgum.disruptor;

import io.jstach.rainbowgum.LogConfig;
import io.jstach.rainbowgum.LogProperties;
import io.jstach.rainbowgum.LogProviderRef;
import io.jstach.rainbowgum.LogPublisher.PublisherFactory;
import io.jstach.rainbowgum.LogPublisher.PublisherProvider;
import io.jstach.rainbowgum.LogPublisherRegistry;
import io.jstach.rainbowgum.annotation.LogConfigurable;
import io.jstach.rainbowgum.spi.RainbowGumServiceProvider;
import io.jstach.rainbowgum.spi.RainbowGumServiceProvider.Configurator;
import io.jstach.svc.ServiceProvider;

/**
 * Registers {@link DisruptorLogPublisher} to the default
 * {@link LogPublisherRegistry#ASYNC_SCHEME} which makes it the default async publisher
 * provider. Disruptor Log Publisher will also be registered to the
 * {@link #DISRUPTOR_SCHEME}.
 */
@ServiceProvider(RainbowGumServiceProvider.class)
public class DisruptorConfigurator implements Configurator {

	/**
	 * The URI scheme for the DisruptorLogPublisher
	 */
	public static final String DISRUPTOR_SCHEME = "disruptor";

	/**
	 * No-Arg constructor for ServiceLoader.
	 */
	public DisruptorConfigurator() {
	}

	@Override
	public boolean configure(LogConfig config, Pass pass) {

		enum DisruptorProvider implements PublisherProvider {

			INSTANT;

			@Override
			public PublisherFactory provide(LogProviderRef ref) {
				return (name, config, appenders) -> {
					return new DisruptorLogBuilder(name).fromProperties(config.properties(), ref) //
						.build() //
						.create(name, config, appenders); //
				};
			}

		}
		config.publisherRegistry().register(LogPublisherRegistry.ASYNC_SCHEME, DisruptorProvider.INSTANT);
		config.publisherRegistry().register(DISRUPTOR_SCHEME, DisruptorProvider.INSTANT);

		return true;
	}

	/**
	 * Default size of disruptor ring buffer.
	 */
	public final static int DEFAULT_BUFFER_SIZE = LogPublisherRegistry.ASYNC_BUFFER_SIZE;

	@LogConfigurable(prefix = LogProperties.PUBLISHER_PREFIX, name = "DisruptorLogBuilder")
	static PublisherFactory of(@LogConfigurable.KeyParameter String name,
			@LogConfigurable.DefaultParameter("DEFAULT_BUFFER_SIZE") Integer bufferSize) {
		return DisruptorLogPublisher.of(bufferSize);
	}

}

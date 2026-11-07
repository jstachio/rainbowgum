package io.jstach.rainbowgum.disruptor;

import java.util.Objects;

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
 * Registers {@link DisruptorLogPublisher} to the {@link #DISRUPTOR_SCHEME}. A route uses
 * it only when its publisher URI has that scheme: having this module on the class path
 * does not change what {@link LogPublisherRegistry#ASYNC_SCHEME} resolves to.
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
					return new DisruptorLogBuilder(name, Objects.requireNonNull(ref.uri().getScheme()))
						.fromProperties(config.properties(), ref) //
						.build() //
						.create(name, config, appenders); //
				};
			}

		}
		config.publisherRegistry().register(DISRUPTOR_SCHEME, DisruptorProvider.INSTANT);

		return true;
	}

	/**
	 * Default size of disruptor ring buffer.
	 */
	public final static int DEFAULT_BUFFER_SIZE = LogPublisherRegistry.ASYNC_BUFFER_SIZE;

	/**
	 * Creates the disruptor publisher. It has no shutdown timeout setting.
	 * @param name route name.
	 * @param scheme publisher scheme as written in the route's publisher uri.
	 * @param bufferSize ring buffer size, which must be a power of two.
	 * @return publisher factory.
	 */
	@LogConfigurable(prefix = LogProperties.PUBLISHER_PREFIX + "{" + LogPublisherRegistry.SCHEME + "}.",
			name = "DisruptorLogBuilder")
	static PublisherFactory of(@LogConfigurable.KeyParameter String name, @LogConfigurable.KeyParameter String scheme,
			@LogConfigurable.DefaultParameter("DEFAULT_BUFFER_SIZE") Integer bufferSize) {
		return DisruptorLogPublisher.of(bufferSize);
	}

}

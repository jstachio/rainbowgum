package io.jstach.rainbowgum.spi;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.ServiceLoader;
import java.util.stream.Stream;

import org.eclipse.jdt.annotation.Nullable;

import io.jstach.rainbowgum.LogConfig;
import io.jstach.rainbowgum.LogProperties;
import io.jstach.rainbowgum.RainbowGum;
import io.jstach.rainbowgum.ServiceRegistry;

/**
 * RainbowGum SPI
 */
public sealed interface RainbowGumServiceProvider {

	/**
	 * Provides properties.
	 */
	public non-sealed interface PropertiesProvider extends RainbowGumServiceProvider {

		/**
		 * Provides properties.
		 * @param registry registry is usually empty here.
		 * @return list of properties.
		 */
		List<LogProperties> provideProperties(ServiceRegistry registry);

	}

	/**
	 * Provides config from registry and properties.
	 */
	public non-sealed interface ConfigProvider extends RainbowGumServiceProvider {

		/**
		 * Provide config from registry and properties.
		 * @param registry registry.
		 * @param properties properties.
		 * @return config.
		 */
		LogConfig provideConfig(ServiceRegistry registry, LogProperties properties);

	}

	/**
	 * Called after {@link LogConfig} has been loaded to do various custom initialization
	 * like registering {@link io.jstach.rainbowgum.LogOutput.OutputProvider}s.
	 * <p>
	 * The configurator has the option to return <code>false</code> to indicate a retry
	 * needs to be made. This is poor mans way to handle dependency needs of one
	 * configurator needing another to run prior.
	 *
	 * @see LogConfig#outputRegistry()
	 * @see ServiceRegistry
	 */
	@FunctionalInterface
	public non-sealed interface Configurator extends RainbowGumServiceProvider {

		/**
		 * Do adhoc initialization before RainbowGum is fully loaded.
		 * @param config config.
		 * @return <code>true</code> if all dependencies were found.
		 */
		boolean configure(LogConfig config);

		/**
		 * The default amount of passes made to resolve configurators.
		 */
		public static int CONFIGURATOR_PASSES = 4;

		/**
		 * Runs configurators. If a configurator returns false then it will be retried.
		 * The default of {@value #CONFIGURATOR_PASSES} passes will be tried.
		 * @param configurators stream of configurators.
		 * @param config config to use for registration.
		 * @throws IllegalStateException if there are configurators still returning
		 * <code>false</code>.
		 */
		public static void runConfigurators(Stream<? extends RainbowGumServiceProvider.Configurator> configurators,
				LogConfig config) {
			runConfigurators(configurators, config, CONFIGURATOR_PASSES);
		}

		/**
		 * Runs configurators. If a configurator returns false then it will be retried.
		 * @param configurators stream of configurators.
		 * @param config config to use for registration.
		 * @param passes number of times to retry configurator if it returns
		 * @throws IllegalStateException if there are configurators still returning
		 * <code>false</code>.
		 */
		public static void runConfigurators(Stream<? extends RainbowGumServiceProvider.Configurator> configurators,
				LogConfig config, int passes) {
			List<Configurator> unresolved = new ArrayList<>(configurators.toList());
			for (int i = 0; i < passes; i++) {
				var it = unresolved.iterator();
				while (it.hasNext()) {
					var c = it.next();
					if (c.configure(config)) {
						it.remove();
					}
				}
				if (unresolved.isEmpty()) {
					break;
				}
			}
			if (!unresolved.isEmpty()) {
				throw new IllegalStateException("Configurators could not find dependencies (returned false) after "
						+ passes + " passes. configurators = " + unresolved);
			}

		}

	}

	/**
	 * Implement to create a custom RainbowGum. This is the preferred way to custommize
	 * RainbowGum publishers, appenders, and outputs.
	 */
	public non-sealed interface RainbowGumProvider extends RainbowGumServiceProvider {

		/**
		 * Optionally provides a rainbow gum based on config.
		 * @param config config loaded.
		 * @return a rainbowgum or not.
		 */
		Optional<RainbowGum> provide(LogConfig config);

		/**
		 * Creates a default rainbow gum from a config.
		 * @param config config.
		 * @return rainbowgum.
		 */
		public static RainbowGum defaults(LogConfig config) {
			return RainbowGum.builder(config).build();
		}

	}

	/**
	 * Finds service providers based on type.
	 * @param <T> provider interface type.
	 * @param loader service loader to use.
	 * @param pt provider class.
	 * @return stream containing only provider of the given type.
	 */
	public static <T extends RainbowGumServiceProvider> Stream<T> findProviders(
			ServiceLoader<RainbowGumServiceProvider> loader, Class<T> pt) {
		return loader.stream().flatMap(p -> {
			if (pt.isAssignableFrom(p.type())) {
				var s = pt.cast(p.get());
				return Stream.of(s);
			}
			return Stream.empty();
		});
	}

	/**
	 * Creates config from service loader.
	 * @param loader service loader.
	 * @return config.
	 */
	public static LogConfig provideConfig(ServiceLoader<RainbowGumServiceProvider> loader) {
		return LogConfig.builder().serviceLoader(loader).build();
	}

	/**
	 * Will load all RainbowGum SPI to create a RainbowGum powered from the ServiceLoader.
	 * @return rainbowgum.
	 */
	public static RainbowGum provide() {
		ServiceLoader<RainbowGumServiceProvider> loader = ServiceLoader.load(RainbowGumServiceProvider.class);
		var config = provideConfig(loader);
		@Nullable
		RainbowGum gum = findProviders(loader, RainbowGumProvider.class).flatMap(s -> s.provide(config).stream())
			.findFirst()
			.orElse(null);

		if (gum == null) {
			gum = RainbowGum.builder(config).build();
		}
		return gum;
	}

}

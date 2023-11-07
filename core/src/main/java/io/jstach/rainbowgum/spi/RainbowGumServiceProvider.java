package io.jstach.rainbowgum.spi;

import java.util.List;
import java.util.Optional;
import java.util.ServiceLoader;
import java.util.stream.Stream;

import org.eclipse.jdt.annotation.Nullable;

import io.jstach.rainbowgum.Defaults;
import io.jstach.rainbowgum.LogConfig;
import io.jstach.rainbowgum.LogOutputProvider;
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
	 * like registering {@link LogOutputProvider}s.
	 *
	 * @see LogConfig#outputRegistry()
	 * @see Defaults#formatterForOutputType(io.jstach.rainbowgum.LogOutput.OutputType)
	 */
	public non-sealed interface Initializer extends RainbowGumServiceProvider {

		/**
		 * Do adhoc initialization before RainbowGum is fully loaded.
		 * @param registry registry.
		 * @param config config.
		 */
		void initialize(ServiceRegistry registry, LogConfig config);

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
			return RainbowGum.builder(config).route(r -> {

			}).build();
		}

	}

	private static <T extends RainbowGumServiceProvider> Stream<T> findProviders(
			ServiceLoader<RainbowGumServiceProvider> loader, Class<T> pt) {
		return loader.stream().flatMap(p -> {
			if (pt.isAssignableFrom(p.type())) {
				var s = pt.cast(p.get());
				return Stream.of(s);
			}
			return Stream.empty();
		});
	}

	private static LogProperties provideProperties(ServiceRegistry registry,
			ServiceLoader<RainbowGumServiceProvider> loader) {
		List<LogProperties> props = findProviders(loader, PropertiesProvider.class)
			.flatMap(s -> s.provideProperties(registry).stream())
			.toList();
		return LogProperties.of(props, LogProperties.StandardProperties.SYSTEM_PROPERTIES);
	}

	private static LogConfig provideConfig(ServiceLoader<RainbowGumServiceProvider> loader, ServiceRegistry registry,
			LogProperties properties) {
		return findProviders(loader, ConfigProvider.class).findFirst()
			.map(s -> s.provideConfig(registry, properties))
			.orElseGet(() -> LogConfig.of(registry, properties));
	}

	private static void runInitializers(ServiceLoader<RainbowGumServiceProvider> loader, ServiceRegistry registry,
			LogConfig config) {
		findProviders(loader, Initializer.class).forEach(c -> c.initialize(registry, config));
	}

	/**
	 * Creates config from service loader.
	 * @param loader service loader.
	 * @return config.
	 */
	public static LogConfig provideConfig(ServiceLoader<RainbowGumServiceProvider> loader) {
		ServiceRegistry registry = ServiceRegistry.of();
		var properties = provideProperties(registry, loader);
		var config = provideConfig(loader, registry, properties);
		runInitializers(loader, registry, config);
		return config;
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

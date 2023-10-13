package io.jstach.rainbowgum.spi;

import java.util.List;
import java.util.Optional;
import java.util.ServiceLoader;
import java.util.stream.Stream;

import org.eclipse.jdt.annotation.Nullable;

import io.jstach.rainbowgum.LogConfig;
import io.jstach.rainbowgum.LogProperties;
import io.jstach.rainbowgum.RainbowGum;

public sealed interface RainbowGumServiceProvider {

	public non-sealed interface PropertiesProvider extends RainbowGumServiceProvider {

		List<LogProperties> provideProperties();

	}

	public non-sealed interface ConfigProvider extends RainbowGumServiceProvider {

		LogConfig provideConfig(LogProperties properties);

	}

	public non-sealed interface Initializer extends RainbowGumServiceProvider {

		void initialize(LogConfig config);

	}

	public non-sealed interface RainbowGumProvider extends RainbowGumServiceProvider {

		Optional<RainbowGum> provide(LogConfig config);

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

	private static LogProperties provideProperties(ServiceLoader<RainbowGumServiceProvider> loader) {
		List<LogProperties> props = findProviders(loader, PropertiesProvider.class)
			.flatMap(s -> s.provideProperties().stream())
			.toList();
		return LogProperties.of(props, LogProperties.StandardProperties.SYSTEM_PROPERTIES);
	}

	private static LogConfig provideConfig(ServiceLoader<RainbowGumServiceProvider> loader, LogProperties properties) {
		return findProviders(loader, ConfigProvider.class).findFirst()
			.map(s -> s.provideConfig(properties))
			.orElseGet(() -> LogConfig.of(properties));
	}

	private static void runInitializers(ServiceLoader<RainbowGumServiceProvider> loader, LogConfig config) {
		findProviders(loader, Initializer.class).forEach(c -> c.initialize(config));
	}

	public static LogConfig provideConfig(ServiceLoader<RainbowGumServiceProvider> loader) {
		var properties = provideProperties(loader);
		var config = provideConfig(loader, properties);
		runInitializers(loader, config);
		return config;
	}

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
		RainbowGum.start(gum);
		return gum;
	}

}

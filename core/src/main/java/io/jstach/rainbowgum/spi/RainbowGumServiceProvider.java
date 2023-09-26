package io.jstach.rainbowgum.spi;

import java.util.Optional;
import java.util.ServiceLoader;
import java.util.stream.Stream;

import org.eclipse.jdt.annotation.Nullable;

import io.jstach.rainbowgum.LogConfig;
import io.jstach.rainbowgum.RainbowGum;

public sealed interface RainbowGumServiceProvider {

	public non-sealed interface ConfigProvider extends RainbowGumServiceProvider {

		LogConfig provideConfig();

	}
	
	public non-sealed interface Initializer extends RainbowGumServiceProvider {
		void initialize(LogConfig config);
	}

	public non-sealed interface RainbowGumProvider extends RainbowGumServiceProvider {

		Optional<RainbowGum> provide(LogConfig config);

	}

	// public non-sealed interface LogAppendersProvider extends RainbowGumServiceProvider
	// {
	// Stream<LogAppender> provide(LogConfig config);
	// }

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

	private static LogConfig provideConfig(ServiceLoader<RainbowGumServiceProvider> loader) {
		return findProviders(loader, ConfigProvider.class).findFirst()
			.map(s -> s.provideConfig())
			.orElseGet(LogConfig::of);
	}

	// private static List<LogAppender> provideAppenders(
	// ServiceLoader<RainbowGumServiceProvider> loader, LogConfig config) {
	// return findProviders(loader, LogAppendersProvider.class)
	// .<LogAppender>flatMap(p -> p.provide(config)).toList();
	// }
	//
	public static RainbowGum provide() {
		ServiceLoader<RainbowGumServiceProvider> loader = ServiceLoader.load(RainbowGumServiceProvider.class);
		var config = provideConfig(loader);
		@Nullable
		RainbowGum gum = findProviders(loader, RainbowGumProvider.class).flatMap(s -> s.provide(config).stream())
			.findFirst()
			.orElse(null);

		if (gum == null) {
			gum = RainbowGum.builder().config(config).build();
		}
		RainbowGum.start(gum);
		return gum;
	}

}

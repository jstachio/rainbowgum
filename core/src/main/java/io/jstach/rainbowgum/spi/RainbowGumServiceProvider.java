package io.jstach.rainbowgum.spi;

import java.util.ServiceLoader;
import java.util.stream.Stream;

import io.jstach.rainbowgum.LogConfig;
import io.jstach.rainbowgum.RainbowGum;

public sealed interface RainbowGumServiceProvider {

	public non-sealed interface ConfigProvider extends RainbowGumServiceProvider {

		LogConfig provideConfig();

	}

	public non-sealed interface RainbowGumProvider extends RainbowGumServiceProvider {

		RainbowGum provide(LogConfig config);

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

	public static RainbowGum provide() {
		ServiceLoader<RainbowGumServiceProvider> loader = ServiceLoader.load(RainbowGumServiceProvider.class);

		LogConfig config = findProviders(loader, ConfigProvider.class).findFirst()
			.map(s -> s.provideConfig())
			.orElseGet(LogConfig::of);

		RainbowGum gum = findProviders(loader, RainbowGumProvider.class).findFirst()
			.map(s -> s.provide(config))
			.orElseGet(() -> RainbowGum.builder().config(config).build());
		RainbowGum.start(gum);
		return gum;
	}

}

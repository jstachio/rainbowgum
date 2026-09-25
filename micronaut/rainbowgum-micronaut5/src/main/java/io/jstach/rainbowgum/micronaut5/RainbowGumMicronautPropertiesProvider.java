package io.jstach.rainbowgum.micronaut5;

import java.util.List;

import io.jstach.rainbowgum.LogProperties;
import io.jstach.rainbowgum.LogProperties.MutableLogProperties;
import io.jstach.rainbowgum.ServiceRegistry;
import io.jstach.rainbowgum.spi.RainbowGumServiceProvider;
import io.jstach.svc.ServiceProvider;

/**
 * Registers a {@link MutableLogProperties} layer, with runtime level changes turned on,
 * into every Rainbow Gum bootstrap, and stores it in the {@link ServiceRegistry} so
 * {@link RainbowGumLoggingSystem} can find and mutate it later.
 * <p>
 * {@code logging.global.change=true} plus a bare (no logger name) {@code
 * logging.change=level} makes every logger, regardless of name, eligible for a runtime
 * level change (matching the exact combination
 * {@code io.jstach.rainbowgum.spring.boot4.PreBootRainbowGumProvider} already uses for
 * the same purpose). Without this, loggers created before a level change is requested
 * would have already been frozen as non-changeable
 * ({@code io.jstach.rainbowgum.slf4j.LevelLogger}, not
 * {@code io.jstach.rainbowgum.slf4j.ReplaceableLogger}), and
 * {@link RainbowGumLoggingSystem#setLogLevel(String, io.micronaut.logging.LogLevel)}
 * would silently have no effect on them.
 */
@ServiceProvider(RainbowGumServiceProvider.class)
public final class RainbowGumMicronautPropertiesProvider implements RainbowGumServiceProvider.PropertiesProvider {

	static final String REGISTRY_NAME = "rainbowgum-micronaut5";

	/**
	 * For {@link java.util.ServiceLoader}.
	 */
	public RainbowGumMicronautPropertiesProvider() {
	}

	@Override
	public List<LogProperties> provideProperties(ServiceRegistry registry) {
		var mutable = MutableLogProperties.builder().description("micronaut").build();
		mutable.put("logging.global.change", "true");
		mutable.put("logging.change", "level");
		registry.put(MutableLogProperties.class, REGISTRY_NAME, mutable);
		return List.of(mutable);
	}

}

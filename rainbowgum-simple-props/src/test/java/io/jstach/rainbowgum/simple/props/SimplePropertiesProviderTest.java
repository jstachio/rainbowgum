package io.jstach.rainbowgum.simple.props;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.junit.jupiter.api.parallel.Isolated;

import io.jstach.rainbowgum.LogConfig;
import io.jstach.rainbowgum.RainbowGum;
import io.jstach.rainbowgum.ServiceRegistry;

/*
 * testServiceLoaderDiscoversProviderAndResolvesFromClasspathFile touches RainbowGum's
 * static current-instance holder (RainbowGum.set/of) - @Isolated/SAME_THREAD keep this
 * from racing other test classes that touch the same global state (see RainbowGumTest's
 * own note) under the "fast" profile's parallel test execution. The other test builds a
 * standalone RainbowGum via RainbowGum.builder(config) instead, which never touches that
 * global state, but stays under the same guard since it is in the same class.
 */
@Isolated
@Execution(ExecutionMode.SAME_THREAD)
class SimplePropertiesProviderTest {

	@Test
	void testServiceLoaderDiscoversProviderAndResolvesFromClasspathFile() {
		RainbowGum.set(RainbowGum::defaults);
		try (var gum = RainbowGum.of()) {
			// src/test/resources/logging.properties has logging.level.root=WARN
			String value = gum.config().properties().forKey("logging.level.root").ofString().value();
			assertEquals("WARN", value);
		}
	}

	@Test
	void testPreRegisteredSimplePropertiesInServiceRegistryWinsOverDefault() {
		// SimplePropertiesProvider.provideProperties uses
		// registry.putIfAbsent(SimpleProperties.class, ...) - proves the "already
		// bound" branch: an application (or a test) that puts its own SimpleProperties
		// in the registry before RainbowGum initializes wins over the default
		// SimpleProperties.builder().build() the provider would otherwise create.
		var registry = ServiceRegistry.of();
		var custom = SimpleProperties.builder()
			.envPrefix("CUSTOM_")
			.envLookup(Map.of("CUSTOM_level_root", "TRACE")::get)
			.resource("classpath:/does-not-exist.properties")
			.build();
		registry.putIfAbsent(SimpleProperties.class, () -> custom);
		var config = LogConfig.builder().serviceRegistry(registry).serviceLoader().build();

		try (var gum = RainbowGum.builder(config).build().start()) {
			String value = gum.config().properties().forKey("logging.level.root").ofString().value();
			assertEquals("TRACE", value);
		}
	}

}

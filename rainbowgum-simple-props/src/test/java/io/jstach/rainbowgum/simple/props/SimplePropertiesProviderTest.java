package io.jstach.rainbowgum.simple.props;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.lang.System.Logger.Level;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.junit.jupiter.api.parallel.Isolated;

import io.jstach.rainbowgum.LogConfig;
import io.jstach.rainbowgum.LogEvent;
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
			String value = gum.config()
				.properties()
				.forKey("logging.level.root")
				.ofString()
				.validateNow(SimplePropertiesProviderTest.class);
			assertEquals("WARN", value);
			assertEquals(List.of("Found profiles: []", "Loaded properties resource: classpath:/logging.properties"),
					gum.config().alerts().dump().stream().map(LogEvent::message).toList());
		}
	}

	@Test
	void testProfileResourceAlertsAreReportedInOrder() {
		var registry = ServiceRegistry.of();
		var selected = SimpleProperties.builder()
			.profiles("profile-first", "profile-second")
			.envLookup(k -> null)
			.build();
		registry.putIfAbsent(SimpleProperties.class, () -> selected);
		var config = LogConfig.builder()
			.serviceRegistry(registry)
			.propertiesProvider(new SimplePropertiesProvider())
			.build();
		assertEquals(
				List.of("Found profiles: [profile-first, profile-second]",
						"Loaded properties resource: classpath:/logging.properties",
						"Loaded properties resource: classpath:/logging-profile-first.properties",
						"Loaded properties resource: classpath:/logging-profile-second.properties"),
				config.alerts().dump().stream().map(LogEvent::message).toList());
		assertEquals(List.of(Level.INFO, Level.INFO, Level.INFO, Level.INFO),
				config.alerts().dump().stream().map(LogEvent::level).toList());
	}

	@Test
	void testMissingBaseResourceReportsInfoAlert() {
		var registry = ServiceRegistry.of();
		var missing = SimpleProperties.builder()
			.resource("classpath:/does-not-exist.properties")
			.profiles("profile-first")
			.envLookup(k -> {
				throw new AssertionError("Environment should not be read without a base resource");
			})
			.build();
		registry.putIfAbsent(SimpleProperties.class, () -> missing);
		var config = LogConfig.builder()
			.serviceRegistry(registry)
			.propertiesProvider(new SimplePropertiesProvider())
			.build();
		assertEquals(List.of("No properties resource found: classpath:/does-not-exist.properties"),
				config.alerts().dump().stream().map(LogEvent::message).toList());
		assertEquals(Level.INFO, config.alerts().dump().get(0).level());
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
			.resource("classpath:/custom.properties")
			.build();
		registry.putIfAbsent(SimpleProperties.class, () -> custom);
		var config = LogConfig.builder().serviceRegistry(registry).serviceLoader().build();

		try (var gum = RainbowGum.builder(config).build().start()) {
			String value = gum.config()
				.properties()
				.forKey("logging.level.root")
				.ofString()
				.validateNow(SimplePropertiesProviderTest.class);
			assertEquals("TRACE", value);
		}
	}

}

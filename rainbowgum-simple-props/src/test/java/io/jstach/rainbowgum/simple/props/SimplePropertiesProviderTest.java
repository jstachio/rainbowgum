package io.jstach.rainbowgum.simple.props;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.lang.System.Logger.Level;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.junit.jupiter.api.parallel.Isolated;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

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

	@ParameterizedTest
	@ValueSource(strings = { "strict-off.properties", "strict-fail.properties" })
	void testStrictFailureRecordsAlertsAndStopsBeforeConfigurators(String resource) {
		var registry = ServiceRegistry.of();
		var builder = SimpleProperties.builder().resource(resource).envLookup(k -> null);
		if (resource.equals("strict-fail.properties")) {
			// the resource's strict=true overrides the builder
			builder.strict(SimpleProperties.StrictType.OFF);
		}
		else {
			builder.strict(SimpleProperties.StrictType.FAIL);
		}
		var simple = builder.build();
		registry.putIfAbsent(SimpleProperties.class, () -> simple);
		var recorded = new ArrayList<LogEvent>();
		var error = assertThrows(IllegalArgumentException.class,
				() -> LogConfig.builder().serviceRegistry(registry).propertiesProvider((services, alerts) -> {
					try {
						return new SimplePropertiesProvider().provideProperties(services, alerts);
					}
					finally {
						recorded.addAll(alerts.dump());
					}
				}).configurator((config, pass) -> {
					throw new AssertionError("Configurators must not run after strict validation fails");
				}).build());
		assertEquals(
				"""
						Invalid simple-props base resource:
						Property key looks like java.util.logging configuration, which Rainbow Gum does not read. key: 'handlers' from SIMPLE_PROPS[%s:1][handlers]
						Property key should start with: 'logging.'. key: 'unqualified.setting' from SIMPLE_PROPS[%s:2][unqualified.setting]"""
					.formatted(resource, resource),
				error.getMessage());
		assertEquals(List.of(
				"Property key looks like java.util.logging configuration, which Rainbow Gum does not read. key: 'handlers' from SIMPLE_PROPS[%s:1][handlers]"
					.formatted(resource),
				"Property key should start with: 'logging.'. key: 'unqualified.setting' from SIMPLE_PROPS[%s:2][unqualified.setting]"
					.formatted(resource)),
				recorded.stream().filter(e -> e.level() == Level.ERROR).map(LogEvent::message).toList());
	}

	@Test
	void testStrictBooleanAliases() {
		assertEquals(SimpleProperties.StrictType.OFF, SimpleProperties.StrictType.parse("false"));
		assertEquals(SimpleProperties.StrictType.FAIL, SimpleProperties.StrictType.parse("true"));
	}

	@Test
	void testStrictPropertyReportsEachUnprefixedBaseResourceEntry() {
		var registry = ServiceRegistry.of();
		var simple = SimpleProperties.builder()
			.resource("strict.properties")
			.strict(SimpleProperties.StrictType.OFF)
			.envLookup(k -> null)
			.build();
		registry.putIfAbsent(SimpleProperties.class, () -> simple);
		var config = LogConfig.builder()
			.serviceRegistry(registry)
			.propertiesProvider(new SimplePropertiesProvider())
			.build();
		var errors = config.alerts().dump().stream().filter(e -> e.level() == Level.ERROR).toList();
		assertEquals(List.of(
				"Property key looks like java.util.logging configuration, which Rainbow Gum does not read. key: 'handlers' from SIMPLE_PROPS[strict.properties:1][handlers]",
				"Property key should start with: 'logging.'. key: 'unqualified.setting' from SIMPLE_PROPS[strict.properties:2][unqualified.setting]"),
				errors.stream().map(LogEvent::message).toList());
	}

	@Test
	void testBuilderCanDisableStrictPropertyValidation() {
		var registry = ServiceRegistry.of();
		var simple = SimpleProperties.builder()
			.resource("strict-off.properties")
			.strict(SimpleProperties.StrictType.OFF)
			.envLookup(k -> null)
			.build();
		registry.putIfAbsent(SimpleProperties.class, () -> simple);
		var config = LogConfig.builder()
			.serviceRegistry(registry)
			.propertiesProvider(new SimplePropertiesProvider())
			.build();
		assertEquals(List.of(), config.alerts().dump().stream().filter(e -> e.level() == Level.ERROR).toList());
	}

	@Test
	void testDefaultProfileResourceAlertsAreReportedInOrder() {
		var registry = ServiceRegistry.of();
		var selected = SimpleProperties.builder().resource("default-profile.properties").envLookup(k -> null).build();
		registry.putIfAbsent(SimpleProperties.class, () -> selected);
		var config = LogConfig.builder()
			.serviceRegistry(registry)
			.propertiesProvider(new SimplePropertiesProvider())
			.build();
		assertEquals(
				List.of("Loading properties from io.jstach.rainbowgum.simple.props.SimplePropertiesProvider",
						"Found profiles: [default]", "Loaded properties resource: default-profile.properties",
						"Loaded properties resource: default-profile-default.properties"),
				config.alerts().dump().stream().map(LogEvent::message).toList());
	}

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
			assertEquals(
					List.of("Loading properties from io.jstach.rainbowgum.simple.props.SimplePropertiesProvider",
							"Found profiles: [default]", "Loaded properties resource: classpath:/logging.properties",
							"Adding configurator: io.jstach.rainbowgum.simple.props.SimplePropertiesProvider"),
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
				List.of("Loading properties from io.jstach.rainbowgum.simple.props.SimplePropertiesProvider",
						"Found profiles: [profile-first, profile-second]",
						"Loaded properties resource: classpath:/logging.properties",
						"Loaded properties resource: classpath:/logging-profile-first.properties",
						"Loaded properties resource: classpath:/logging-profile-second.properties"),
				config.alerts().dump().stream().map(LogEvent::message).toList());
		assertEquals(List.of(Level.INFO, Level.INFO, Level.INFO, Level.INFO, Level.INFO),
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
		assertEquals(
				List.of("Loading properties from io.jstach.rainbowgum.simple.props.SimplePropertiesProvider",
						"No properties resource found: classpath:/does-not-exist.properties"),
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

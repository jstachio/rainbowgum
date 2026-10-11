package io.jstach.rainbowgum;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.junit.jupiter.api.parallel.Isolated;

import io.jstach.rainbowgum.LogConfig.DebugModeType;

/*
 * Core alone, no property files: someone trying things with -D on the command line.
 * Sets real system properties, so it must not run alongside other tests.
 */
@Isolated
@Execution(ExecutionMode.SAME_THREAD)
class DebugHelpSystemPropertiesTest {

	@ParameterizedTest
	@CsvSource({ "logging.simpleprops.levelFlie, watch, logging.simpleprops.levelFile",
			"logging.simpleprops.levelFilePaht, level.properties, logging.simpleprops.levelFilePath",
			"logging.simpleprops.strcit, short, logging.simpleprops.strict" })
	void misspelledBootstrapSettingsFailWithTheirKnownKey(String key, String value, String correctKey) {
		System.setProperty(key, value);
		try {
			var config = LogConfig.builder().debug(DebugModeType.HELP).build();
			var gum = RainbowGum.builder(config).build();
			var e = assertThrows(IllegalStateException.class, gum::start);
			assertEquals(
					"""
							1 alert(s) at warning or above were recorded while starting and logging.debug=help:
							[WARNING] Property key 'KEY' from SYSTEM_PROPERTIES[KEY] was set but not read during startup. Did you mean 'CORRECT_KEY'?"""
						.replace("CORRECT_KEY", correctKey)
						.replace("KEY", key),
					e.getMessage());
		}
		finally {
			System.getProperties().remove(key);
		}
	}

	@Test
	void knownBootstrapSettingsAreExemptWithoutAPropertiesProvider() {
		var settings = Map.of("logging.simpleprops.strict", "short", "logging.simpleprops.levelFile", "off",
				"logging.simpleprops.levelFilePath", "level.properties");
		settings.forEach(System::setProperty);
		try {
			var config = LogConfig.builder().debug(DebugModeType.HELP).build();
			try (var gum = RainbowGum.builder(config).build().start()) {
				assertEquals(List.of(),
						gum.config()
							.alerts()
							.dump()
							.stream()
							.filter(e -> e.message().startsWith("Property key"))
							.toList());
			}
		}
		finally {
			settings.keySet().forEach(key -> System.getProperties().remove(key));
		}
	}

	@Test
	void misspelledSystemPropertiesFailWithTheKeyTheyProbablyMeant() {
		var properties = Map.of( //
				"logging.appendrs", "console", // typo of a key read
				"logging.appender.console.encodr.uri", "json5"); // typo of a key read
		properties.forEach(System::setProperty);
		try {
			var config = LogConfig.builder().debug(DebugModeType.HELP).build();
			var gum = RainbowGum.builder(config).build();
			var e = assertThrows(IllegalStateException.class, gum::start);
			String expected = """
					2 alert(s) at warning or above were recorded while starting and logging.debug=help:
					[WARNING] Property key 'logging.appender.console.encodr.uri' from SYSTEM_PROPERTIES[logging.appender.console.encodr.uri] was set but not read during startup. Did you mean 'logging.appender.console.encoder.uri'?
					[WARNING] Property key 'logging.appendrs' from SYSTEM_PROPERTIES[logging.appendrs] was set but not read during startup. Did you mean 'logging.appenders'?""";
			assertEquals(expected, e.getMessage());
		}
		finally {
			properties.keySet().forEach(System::clearProperty);
		}
	}

	@ParameterizedTest
	@CsvSource({ "logging.encoder.console.level, plain, logging.appender.console.encoder.ttll.level",
			"logging.encoder.ttll.console.level, plain, logging.appender.console.encoder.ttll.level",
			"logging.appender.console.encoder.level, plain, logging.appender.console.encoder.ttll.level",
			"logging.output.console.stream, follow, logging.appender.console.output.stdout.stream",
			"logging.output.stdout.console.stream, follow, logging.appender.console.output.stdout.stream",
			"logging.appender.console.encoder, ttll, logging.appender.console.encoder.uri" })
	void movedKeySuggestsItsNewKey(String key, String value, String typedKey) {
		System.setProperty(key, value);
		try {
			var config = LogConfig.builder().debug(DebugModeType.HELP).build();
			var gum = RainbowGum.builder(config).build();
			var e = assertThrows(IllegalStateException.class, gum::start);
			assertEquals(
					"""
							1 alert(s) at warning or above were recorded while starting and logging.debug=help:
							[WARNING] Property key 'KEY' from SYSTEM_PROPERTIES[KEY] was set but not read during startup. Did you mean 'TYPED'?"""
						.replace("KEY", key)
						.replace("TYPED", typedKey),
					e.getMessage());
		}
		finally {
			System.getProperties().remove(key);
		}
	}

	@ParameterizedTest
	@CsvSource({ "logging.publisher.default.bufferSize, logging.route.default.publisher.async.bufferSize",
			"logging.route.default.publisher.bufferSize, logging.route.default.publisher.async.bufferSize" })
	void movedPublisherKeySuggestsItsNewKey(String key, String typedKey) {
		var properties = Map.of("logging.route.default.publisher.uri", "async", key, "64");
		properties.forEach(System::setProperty);
		try {
			var config = LogConfig.builder().debug(DebugModeType.HELP).build();
			var gum = RainbowGum.builder(config).build();
			var e = assertThrows(IllegalStateException.class, gum::start);
			assertEquals(
					"""
							1 alert(s) at warning or above were recorded while starting and logging.debug=help:
							[WARNING] Property key 'KEY' from SYSTEM_PROPERTIES[KEY] was set but not read during startup. Did you mean 'TYPED'?"""
						.replace("KEY", key)
						.replace("TYPED", typedKey),
					e.getMessage());
		}
		finally {
			properties.keySet().forEach(System::clearProperty);
		}
	}

	@Test
	void oldPublisherKeySuggestsUri() {
		String key = "logging.route.default.publisher";
		System.setProperty(key, "async");
		try {
			var config = LogConfig.builder().debug(DebugModeType.HELP).build();
			var gum = RainbowGum.builder(config).build();
			var e = assertThrows(IllegalStateException.class, gum::start);
			assertEquals(
					"""
							1 alert(s) at warning or above were recorded while starting and logging.debug=help:
							[WARNING] Property key 'logging.route.default.publisher' from SYSTEM_PROPERTIES[logging.route.default.publisher] was set but not read during startup. Did you mean 'logging.route.default.publisher.uri'?""",
					e.getMessage());
		}
		finally {
			System.clearProperty(key);
		}
	}

	/*
	 * A key of another encoder type is what switching encoder.uri leaves behind, so it is
	 * only noted, not warned about with a suggestion of the current type's key.
	 */
	@Test
	void otherTypesKeyIsNotWarnedAbout() {
		String key = "logging.appender.console.encoder.json5.level";
		System.setProperty(key, "plain");
		try {
			var config = LogConfig.builder().debug(DebugModeType.HELP).build();
			try (var gum = RainbowGum.builder(config).build().start()) {
				var alerts = gum.config()
					.alerts()
					.dump()
					.stream()
					.filter(e -> e.message().startsWith("Property key"))
					.map(e -> e.level() + " " + e.message())
					.toList();
				assertEquals(List.of("INFO Property key '" + key + "' from SYSTEM_PROPERTIES[" + key
						+ "] was set but not read during startup."), alerts);
			}
		}
		finally {
			System.getProperties().remove(key);
		}
	}

	@Test
	void mutableCompositesStillCheckSystemPropertyTypos() {
		System.setProperty("logging.appendrs", "console");
		try {
			var properties = LogProperties.MutableLogProperties.builder()
				.with(LogProperties.StandardProperties.SYSTEM_PROPERTIES)
				.build();
			var config = LogConfig.builder().properties(properties).debug(DebugModeType.HELP).build();
			var gum = RainbowGum.builder(config).build();
			var e = assertThrows(IllegalStateException.class, gum::start);
			assertEquals(
					"""
							1 alert(s) at warning or above were recorded while starting and logging.debug=help:
							[WARNING] Property key 'logging.appendrs' from SYSTEM_PROPERTIES[logging.appendrs] was set but not read during startup. Did you mean 'logging.appenders'?""",
					e.getMessage());
		}
		finally {
			System.getProperties().remove("logging.appendrs");
		}
	}

	@Test
	void recordingPreservesNestedPrecedenceAndMutableWrites() {
		System.setProperty("logging.example.mode", "system");
		try {
			var systemFallback = LogProperties.MutableLogProperties.builder()
				.with(LogProperties.StandardProperties.SYSTEM_PROPERTIES)
				.build();
			var higherPriority = LogProperties.builder()
				.fromProperties("logging.example.mode=explicit")
				.order(100)
				.build();
			var properties = LogProperties.MutableLogProperties.builder()
				.with(systemFallback)
				.with(higherPriority)
				.build();
			var config = LogConfig.builder().properties(properties).debug(DebugModeType.HELP).build();
			assertEquals("explicit",
					config.properties().forKey("logging.example.mode").ofString().validateNow(getClass()));
			var mutable = (LogProperties.MutableLogProperties) config.properties();
			mutable.put("logging.example.new", "updated");
			assertEquals("updated", properties.valueOrNull("logging.example.new"));
			assertEquals("updated",
					config.properties().forKey("logging.example.new").ofString().validateNow(getClass()));
			try (var gum = RainbowGum.builder(config).build().start()) {
				assertEquals(0L,
						gum.config()
							.alerts()
							.dump()
							.stream()
							.filter(e -> e.message().startsWith("Property key"))
							.count());
			}
		}
		finally {
			System.getProperties().remove("logging.example.mode");
		}
	}

}

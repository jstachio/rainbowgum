package io.jstach.rainbowgum;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.Map;

import org.junit.jupiter.api.Test;
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

	@Test
	void misspelledSystemPropertiesFailWithTheKeyTheyProbablyMeant() {
		var properties = Map.of( //
				"logging.appendrs", "console", // typo of a key read
				"logging.appender.console.encodr", "json5"); // typo of a key read
		properties.forEach(System::setProperty);
		try {
			var config = LogConfig.builder().debug(DebugModeType.HELP).build();
			var gum = RainbowGum.builder(config).build();
			var e = assertThrows(IllegalStateException.class, gum::start);
			String expected = """
					2 alert(s) at warning or above were recorded while starting and logging.debug=help:
					[WARNING] Property key 'logging.appender.console.encodr' from SYSTEM_PROPERTIES[logging.appender.console.encodr] was set but not read during startup. Did you mean 'logging.appender.console.encoder'?
					[WARNING] Property key 'logging.appendrs' from SYSTEM_PROPERTIES[logging.appendrs] was set but not read during startup. Did you mean 'logging.appenders'?""";
			assertEquals(expected, e.getMessage());
		}
		finally {
			properties.keySet().forEach(System::clearProperty);
		}
	}

	@Test
	void misspelledLevelAndProfileKeysSuggestTheRealKey() {
		var properties = Map.of( //
				"logging.levle", "DEBUG", // root level, read lazily
				"logging.levels.com.example", "DEBUG", // logger level with a plural
														// prefix
				"logging.profile", "json"); // read by simple props before LogConfig
		properties.forEach(System::setProperty);
		try {
			var config = LogConfig.builder().debug(DebugModeType.HELP).build();
			var gum = RainbowGum.builder(config).build();
			var e = assertThrows(IllegalStateException.class, gum::start);
			String expected = """
					3 alert(s) at warning or above were recorded while starting and logging.debug=help:
					[WARNING] Property key 'logging.levels.com.example' from SYSTEM_PROPERTIES[logging.levels.com.example] was set but not read during startup. Did you mean 'logging.level.com.example'?
					[WARNING] Property key 'logging.levle' from SYSTEM_PROPERTIES[logging.levle] was set but not read during startup. Did you mean 'logging.level'?
					[WARNING] Property key 'logging.profile' from SYSTEM_PROPERTIES[logging.profile] was set but not read during startup. Did you mean 'logging.profiles'?""";
			assertEquals(expected, e.getMessage());
		}
		finally {
			properties.keySet().forEach(System::clearProperty);
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

package io.jstach.rainbowgum.simple.props;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.Map;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.junit.jupiter.api.parallel.Isolated;

import io.jstach.rainbowgum.LogConfig;
import io.jstach.rainbowgum.LogAlerts.FailLevel;
import io.jstach.rainbowgum.LogConfig.DebugModeType;
import io.jstach.rainbowgum.LogProperty.ValidationException;
import io.jstach.rainbowgum.LogProperties;
import io.jstach.rainbowgum.LogProvider;
import io.jstach.rainbowgum.RainbowGum;
import io.jstach.rainbowgum.output.ListLogOutput;

/*
 * Sets real system properties, so it must not run alongside other tests.
 */
@Isolated
@Execution(ExecutionMode.SAME_THREAD)
class UnusedPropertyCheckTest {

	@Test
	void debugAllAlertsUnusedFileKeysWithTheReadKeyTheyProbablyMeant() {
		var config = config("classpath:/unused-keys.properties", DebugModeType.ALL);
		try (var gum = RainbowGum.builder(config).build().start()) {
			String expected = """
					[WARNING] Property key 'logging.encoder.list.keyvalues' from SIMPLE_PROPS[classpath:/unused-keys.properties:4][logging.encoder.list.keyvalues] was set but not read during startup. Did you mean 'logging.encoder.list.keyValues'?
					[WARNING] Property key 'logging.encoder.list.KEYVALUES' from SIMPLE_PROPS[classpath:/unused-keys.properties:5][logging.encoder.list.KEYVALUES] was set but not read during startup. Did you mean 'logging.encoder.list.keyValues'?
					[WARNING] Property key 'logging.encoder.list.key-values' from SIMPLE_PROPS[classpath:/unused-keys.properties:6][logging.encoder.list.key-values] was set but not read during startup. Did you mean 'logging.encoder.list.keyValues'?
					[WARNING] Property key 'logging.encoder.list.therad' from SIMPLE_PROPS[classpath:/unused-keys.properties:7][logging.encoder.list.therad] was set but not read during startup. Did you mean 'logging.encoder.list.thread'?
					[WARNING] Property key 'logging.encoder.list.levle' from SIMPLE_PROPS[classpath:/unused-keys.properties:8][logging.encoder.list.levle] was set but not read during startup. Did you mean 'logging.encoder.list.level'?
					[INFO] Property key 'logging.encoder.list.made.up' from SIMPLE_PROPS[classpath:/unused-keys.properties:9][logging.encoder.list.made.up] was set but not read during startup.""";
			assertEquals(expected, unusedKeyAlerts(gum));
		}
	}

	@Test
	void debugHelpFailsOnMisspelledSystemProperties() {
		var properties = Map.of( //
				"logging.encoder.list.thread", "id", // read
				"logging.encoder.list.keyvalues", "json", // typo
				"logging.encoder.list.levle", "plain", // typo of a key set in the file
				"logging.encoder.list.made.up", "x", // unknown, no suggestion
				"logging.config", "classpath:other.xml", // another library's
				"logging.global.queue.level", "INFO", // read before config
				"logging.level.com.example", "DEBUG"); // level
		properties.forEach(System::setProperty);
		try {
			var config = config("classpath:/unused-system-properties.properties", DebugModeType.HELP);
			var gum = RainbowGum.builder(config).build();
			var e = assertThrows(IllegalStateException.class, gum::start);
			String expected = """
					2 alert(s) at warning or above were recorded while starting and logging.debug=help:
					[WARNING] Property key 'logging.encoder.list.keyvalues' from SYSTEM_PROPERTIES[logging.encoder.list.keyvalues] was set but not read during startup. Did you mean 'logging.encoder.list.keyValues'?
					[WARNING] Property key 'logging.encoder.list.levle' from SYSTEM_PROPERTIES[logging.encoder.list.levle] was set but not read during startup. Did you mean 'logging.encoder.list.level'?""";
			assertEquals(expected, e.getMessage());
		}
		finally {
			properties.keySet().forEach(System::clearProperty);
		}
	}

	private static LogConfig config(String resource, DebugModeType debug) {
		return config(resource, debug, FailLevel.OFF);
	}

	private static LogConfig config(String resource, DebugModeType debug, FailLevel alertsFail) {
		var simple = SimpleProperties.builder().resource(resource).envLookup(k -> null).build();
		var config = LogConfig.builder()
			.properties(LogProperties.of(simple.properties()))
			.debug(debug)
			.alertsFail(alertsFail)
			.build();
		config.outputRegistry().register("list", ref -> LogProvider.of(new ListLogOutput()));
		return config;
	}

	@Test
	void alertsFailWarningInAFileFailsOnAMisspelledKeyWithoutDebug() {
		var gum = RainbowGum.builder(config("classpath:/alerts-fail-warning.properties", DebugModeType.OFF)).build();
		var e = assertThrows(IllegalStateException.class, gum::start);
		String expected = """
				1 alert(s) at warning or above were recorded while starting and logging.alerts.fail=warning:
				[WARNING] Property key 'logging.encoder.list.therad' from SIMPLE_PROPS[classpath:/alerts-fail-warning.properties:5][logging.encoder.list.therad] was set but not read during startup. Did you mean 'logging.encoder.list.thread'?""";
		assertEquals(expected, e.getMessage());
	}

	@Test
	void alertsFailWarningFromTheBuilder() {
		var config = config("classpath:/alerts-fail-typo.properties", DebugModeType.OFF, FailLevel.WARNING);
		var gum = RainbowGum.builder(config).build();
		var e = assertThrows(IllegalStateException.class, gum::start);
		String expected = """
				1 alert(s) at warning or above were recorded while starting and logging.alerts.fail=warning:
				[WARNING] Property key 'logging.encoder.list.therad' from SIMPLE_PROPS[classpath:/alerts-fail-typo.properties:4][logging.encoder.list.therad] was set but not read during startup. Did you mean 'logging.encoder.list.thread'?""";
		assertEquals(expected, e.getMessage());
	}

	@Test
	void alertsFailErrorStartsDespiteAMisspelledKey() {
		var config = config("classpath:/alerts-fail-typo.properties", DebugModeType.ALL, FailLevel.ERROR);
		try (var gum = RainbowGum.builder(config).build().start()) {
			String expected = """
					[WARNING] Property key 'logging.encoder.list.therad' from SIMPLE_PROPS[classpath:/alerts-fail-typo.properties:4][logging.encoder.list.therad] was set but not read during startup. Did you mean 'logging.encoder.list.thread'?
					[INFO] Property key 'logging.encoder.list.made.up' from SIMPLE_PROPS[classpath:/alerts-fail-typo.properties:5][logging.encoder.list.made.up] was set but not read during startup.""";
			assertEquals(expected, unusedKeyAlerts(gum));
		}
	}

	@Test
	void invalidAlertsFailValueFails() {
		var e = assertThrows(ValidationException.class,
				() -> config("classpath:/alerts-fail-invalid.properties", DebugModeType.OFF));
		String expected = """
				Validation failed for io.jstach.rainbowgum.LogAlerts:
				Error for property. key: 'logging.alerts.fail' from SIMPLE_PROPS[classpath:/alerts-fail-invalid.properties:1][logging.alerts.fail], 'loud' is not a valid value for io.jstach.rainbowgum.LogAlerts.FailLevel. Valid values: 'off', 'error', 'warning', 'true', 'false'
				Tried:
				    'logging.alerts.fail' from:
				        SYSTEM_PROPERTIES[logging.alerts.fail],
				        ENV[RAINBOWGUM_alerts_fail],
				        SIMPLE_PROPS[classpath:/alerts-fail-invalid.properties:1][logging.alerts.fail]""";
		assertEquals(expected, e.getMessage());
	}

	private static String unusedKeyAlerts(RainbowGum gum) {
		return gum.config()
			.alerts()
			.dump()
			.stream()
			.filter(e -> e.message().startsWith("Property key"))
			.map(e -> "[" + e.level() + "] " + e.message())
			.collect(Collectors.joining("\n"));
	}

}

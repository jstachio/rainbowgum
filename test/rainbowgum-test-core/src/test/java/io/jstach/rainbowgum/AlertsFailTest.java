package io.jstach.rainbowgum;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.lang.System.Logger.Level;
import java.util.concurrent.atomic.AtomicBoolean;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import io.jstach.rainbowgum.LogAlerts.FailLevel;
import io.jstach.rainbowgum.output.ListLogOutput;

/*
 * Alerts recorded while LogConfig is built, here by a configurator, are checked when the
 * build finishes.
 */
class AlertsFailTest {

	private static LogConfig.Builder builder(FailLevel failLevel, Level alertLevel) {
		return LogConfig.builder()
			.properties(LogProperties.StandardProperties.EMPTY)
			.alertsFail(failLevel)
			.configurator((config, pass) -> {
				config.alerts()
					.alert(LogEventFactory.of("test").eventNoArg(alertLevel, "something went " + alertLevel, null));
				return true;
			});
	}

	@ParameterizedTest
	@CsvSource({ "off, OFF", "false, OFF", "error, ERROR", "warning, WARNING", "warn, WARNING", "WARN, WARNING",
			"true, WARNING" })
	void parses(String value, FailLevel expected) {
		assertEquals(expected, FailLevel.parse(value));
	}

	@Test
	void parseInvalid() {
		var e = assertThrows(IllegalArgumentException.class, () -> FailLevel.parse("warnings"));
		assertEquals("'warnings' is not a valid value for io.jstach.rainbowgum.LogAlerts.FailLevel. "
				+ "Valid values: 'off', 'error', 'warning', 'true', 'false', 'warn'", e.getMessage());
	}

	@ParameterizedTest
	@CsvSource({ "OFF, ERROR", "OFF, WARNING", "ERROR, WARNING", "ERROR, INFO", "WARNING, INFO" })
	void builds(FailLevel failLevel, Level alertLevel) {
		builder(failLevel, alertLevel).build();
	}

	@ParameterizedTest
	@CsvSource({ "ERROR, ERROR", "WARNING, ERROR", "WARNING, WARNING" })
	void fails(FailLevel failLevel, Level alertLevel) {
		var e = assertThrows(IllegalStateException.class, () -> builder(failLevel, alertLevel).build());
		String level = failLevel.name().toLowerCase(java.util.Locale.ROOT);
		assertEquals(
				"1 alert(s) at " + level + " or above were recorded while building the configuration and "
						+ "logging.alerts.fail=" + level + ":\n[" + alertLevel + "] something went " + alertLevel,
				e.getMessage());
	}

	@ParameterizedTest
	@CsvSource({ "ERROR, ERROR, false", "WARNING, ERROR, false", "WARNING, WARNING, false", "ERROR, ERROR, true",
			"WARNING, ERROR, true", "WARNING, WARNING, true" })
	void failsEvenWhenTheDiagnosticQueueLosesTheAlert(FailLevel failLevel, Level alertLevel, boolean clear) {
		var properties = LogProperties.builder().fromProperties("logging.alerts.capacity=1").build();
		var e = assertThrows(IllegalStateException.class,
				() -> LogConfig.builder().properties(properties).alertsFail(failLevel).configurator((config, pass) -> {
					config.alerts().alert(LogEventFactory.of("test").eventNoArg(alertLevel, "startup failure", null));
					if (clear) {
						config.alerts().clear();
					}
					config.alerts().info(AlertsFailTest.class, "later information");
					assertEquals(1, config.alerts().stats().capacity());
					assertEquals("later information", config.alerts().dump().getFirst().message());
					return true;
				}).build());
		String expected = """
				1 alert(s) at FAIL_LEVEL or above were recorded while building the configuration and logging.alerts.fail=FAIL_LEVEL:
				[ALERT_LEVEL] startup failure"""
			.replace("FAIL_LEVEL", failLevel.name().toLowerCase(java.util.Locale.ROOT))
			.replace("ALERT_LEVEL", alertLevel.name());
		assertEquals(expected, e.getMessage());
	}

	@Test
	void reportsTheTotalEvenWhenSeveralFailuresAreEvicted() {
		var properties = LogProperties.builder().fromProperties("logging.alerts.capacity=1").build();
		var e = assertThrows(IllegalStateException.class,
				() -> LogConfig.builder()
					.properties(properties)
					.alertsFail(FailLevel.WARNING)
					.configurator((config, pass) -> {
						config.alerts().warn(AlertsFailTest.class, "first failure");
						config.alerts().warn(AlertsFailTest.class, "second failure");
						config.alerts().warn(AlertsFailTest.class, "third failure");
						config.alerts().info(AlertsFailTest.class, "later information");
						return true;
					})
					.build());
		assertEquals(
				"""
						3 alert(s) at warning or above were recorded while building the configuration and logging.alerts.fail=warning:
						[WARNING] first failure
						2 additional failing alert(s) are no longer retained.""",
				e.getMessage());
	}

	@ParameterizedTest
	@CsvSource({ "ERROR, ERROR", "WARNING, ERROR", "WARNING, WARNING" })
	void failsAndClosesTheOutputWhenAnAlertIsEvictedDuringStartup(FailLevel failLevel, Level alertLevel) {
		var properties = LogProperties.builder().fromProperties("logging.alerts.capacity=1").build();
		var config = LogConfig.builder().properties(properties).alertsFail(failLevel).build();
		var closed = new AtomicBoolean();
		var output = new ListLogOutput() {
			@Override
			public void start(LogConfig c) {
				c.alerts().alert(LogEventFactory.of("test").eventNoArg(alertLevel, "output startup failure", null));
				c.alerts().info(AlertsFailTest.class, "output startup finished");
			}

			@Override
			public void close() {
				closed.set(true);
			}
		};
		var gum = RainbowGum.builder(config).route(r -> r.appender("list", a -> a.output(output))).build();
		var e = assertThrows(IllegalStateException.class, gum::start);
		String expected = """
				1 alert(s) at FAIL_LEVEL or above were recorded while starting and logging.alerts.fail=FAIL_LEVEL:
				[ALERT_LEVEL] output startup failure"""
			.replace("FAIL_LEVEL", failLevel.name().toLowerCase(java.util.Locale.ROOT))
			.replace("ALERT_LEVEL", alertLevel.name());
		assertEquals(expected, e.getMessage());
		assertEquals(true, closed.get());
	}

}

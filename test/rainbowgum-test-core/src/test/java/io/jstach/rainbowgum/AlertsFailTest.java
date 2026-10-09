package io.jstach.rainbowgum;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.lang.System.Logger.Level;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import io.jstach.rainbowgum.LogAlerts.FailLevel;

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

}

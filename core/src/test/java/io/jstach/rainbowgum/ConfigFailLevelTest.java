package io.jstach.rainbowgum;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.lang.System.Logger.Level;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import io.jstach.rainbowgum.spi.RainbowGumServiceProvider;

/*
 * By default only alerts about configuration fail startup. A problem while logging, such
 * as an appender failing to write, is reported but does not stop the application.
 */
class ConfigFailLevelTest {

	private static RainbowGum gum(String properties, RainbowGumServiceProvider.Configurator configurator) {
		var config = LogConfig.builder()
			.properties(LogProperties.builder().fromProperties(properties).build())
			.configurator(configurator)
			.build();
		return RainbowGum.builder(config).build();
	}

	@Test
	void loggingProblemsDoNotFailByDefault() {
		try (var gum = gum("", (c, pass) -> {
			c.alerts().warn(ConfigFailLevelTest.class, "discarded events");
			c.alerts().error(ConfigFailLevelTest.class, "append failed", new RuntimeException("append failed"));
			return true;
		})) {
			assertDoesNotThrow(gum::start);
		}
	}

	@Test
	void configurationWarningFailsByDefault() {
		var e = assertThrows(IllegalStateException.class, () -> gum("", (c, pass) -> {
			c.alerts().config(ConfigFailLevelTest.class, Level.WARNING, "bad setting", null);
			return true;
		}));
		assertEquals("""
				1 configuration alert(s) at warning or above were recorded while building the configuration and \
				logging.alerts.fail=config (the default):
				[WARNING] bad setting""", e.getMessage());
	}

	@Test
	void configurationInformationDoesNotFail() {
		try (var gum = gum("", (c, pass) -> {
			c.alerts().config(ConfigFailLevelTest.class, Level.INFO, "note", null);
			return true;
		})) {
			assertDoesNotThrow(gum::start);
		}
	}

	@Test
	void warningFailsOnLoggingProblemsToo() {
		var e = assertThrows(IllegalStateException.class, () -> gum("logging.alerts.fail=warning", (c, pass) -> {
			c.alerts().warn(ConfigFailLevelTest.class, "discarded events");
			return true;
		}));
		assertEquals("""
				1 alert(s) at warning or above were recorded while building the configuration and \
				logging.alerts.fail=warning:
				[WARNING] discarded events""", e.getMessage());
	}

	@ParameterizedTest
	@CsvSource({ "OFF, WARNING, true, false", "CONFIG, WARNING, true, true", "CONFIG, ERROR, true, true",
			"CONFIG, INFO, true, false", "CONFIG, ERROR, false, false", "ERROR, ERROR, false, true",
			"ERROR, WARNING, true, false", "WARNING, WARNING, false, true", "WARNING, INFO, true, false" })
	void fails(LogAlerts.FailLevel failLevel, Level level, boolean config, boolean expected) {
		assertEquals(expected, failLevel.fails(level, config));
	}

	@Test
	void configIsParsed() {
		assertEquals(LogAlerts.FailLevel.CONFIG, LogAlerts.FailLevel.parse("config"));
	}

}

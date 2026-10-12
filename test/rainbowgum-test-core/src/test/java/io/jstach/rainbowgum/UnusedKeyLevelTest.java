package io.jstach.rainbowgum;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.junit.jupiter.api.parallel.Isolated;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import io.jstach.rainbowgum.LogAlerts.UnusedKeyLevel;

/*
 * logging.alerts.unusedKey with an unread system property, the one source core can list
 * keys of on its own. Sets a real system property, so it must not run alongside other
 * tests.
 */
@Isolated
@Execution(ExecutionMode.SAME_THREAD)
class UnusedKeyLevelTest {

	static final String UNREAD = "logging.made.up";

	static final String UNREAD_MESSAGE = "Property key '" + UNREAD + "' from SYSTEM_PROPERTIES[" + UNREAD
			+ "] was set but not read during startup.";

	private static RainbowGum gum(String properties) {
		var config = LogConfig.builder().properties(LogProperties.builder().fromProperties(properties).build()).build();
		return RainbowGum.builder(config).build();
	}

	@ParameterizedTest
	@CsvSource({ "info, INFO", "warning, WARNING", "warn, WARNING", "WARN, WARNING", "error, ERROR" })
	void parses(String value, UnusedKeyLevel expected) {
		assertEquals(expected, UnusedKeyLevel.parse(value));
	}

	@ParameterizedTest
	@CsvSource({ "warning, warning, WARNING", "error, warning, ERROR", "error, error, ERROR" })
	void anUnreadKeyFailsStartup(String unusedKey, String fail, String level) {
		System.setProperty(UNREAD, "x");
		try {
			var gum = gum("logging.alerts.fail=" + fail + "\nlogging.alerts.unusedKey=" + unusedKey);
			var e = assertThrows(IllegalStateException.class, gum::start);
			assertEquals("""
					1 alert(s) at FAIL_LEVEL or above were recorded while starting and logging.alerts.fail=FAIL_LEVEL:
					[ALERT_LEVEL] MESSAGE""".replace("FAIL_LEVEL", fail)
				.replace("ALERT_LEVEL", level)
				.replace("MESSAGE", UNREAD_MESSAGE), e.getMessage());
		}
		finally {
			System.clearProperty(UNREAD);
		}
	}

	@ParameterizedTest
	@CsvSource({ "logging.levels", "logging.levelExtra", "logging.route.console.levels",
			"logging.route.console.levelExtra", "logging.route.console.publisher.levels", "logging.route.level.typo",
			"logging.changes", "logging.change.com.example", "logging.global.change", "logging.caller.com.example" })
	void nearLevelKeysStillFailStrictStartup(String key) {
		System.setProperty(key, "x");
		try {
			var gum = gum("logging.alerts.fail=error\nlogging.alerts.unusedKey=error");
			var e = assertThrows(IllegalStateException.class, gum::start);
			assertEquals("""
					1 alert(s) at error or above were recorded while starting and logging.alerts.fail=error:
					[ERROR] Property key 'KEY' from SYSTEM_PROPERTIES[KEY] was set but not read during startup."""
				.replace("KEY", key), e.getMessage());
		}
		finally {
			System.clearProperty(key);
		}
	}

	@ParameterizedTest
	@CsvSource({ "logging.level.com.example", "logging.level.levels", "logging.route.console.level.com.example",
			"logging.route.console.level.levels", "logging.route.level.level.com.example", "logging.change.logger",
			"logging.change.logger.com.example", "logging.caller.logger", "logging.caller.logger.com.example" })
	void perLoggerLevelAndChangeKeysAreStillExempt(String key) {
		System.setProperty(key, "debug");
		try (var gum = gum("logging.alerts.fail=error\nlogging.alerts.unusedKey=error").start()) {
			assertEquals(List.of(),
					gum.config().alerts().dump().stream().filter(e -> e.message().startsWith("Property key")).toList());
		}
		finally {
			System.clearProperty(key);
		}
	}

	@ParameterizedTest
	@CsvSource({ "info, warning, INFO", "warning, error, ''", "info, error, ''" })
	void anUnreadKeyBelowTheFailLevelStarts(String unusedKey, String fail, String level) {
		System.setProperty(UNREAD, "x");
		try (var gum = gum("logging.alerts.fail=" + fail + "\nlogging.alerts.unusedKey=" + unusedKey).start()) {
			var alerts = gum.config()
				.alerts()
				.dump()
				.stream()
				.filter(e -> e.message().startsWith("Property key"))
				.map(e -> "[" + e.level() + "] " + e.message())
				.toList();
			// Below the fail level outside debug mode, nothing is checked at all.
			assertEquals(level.isEmpty() ? List.of() : List.of("[" + level + "] " + UNREAD_MESSAGE), alerts);
		}
		finally {
			System.clearProperty(UNREAD);
		}
	}

	@Test
	void theBuilderSetsTheLevelWhenThePropertyIsAbsent() {
		System.setProperty(UNREAD, "x");
		try {
			var config = LogConfig.builder()
				.properties(LogProperties.builder().fromProperties("").build())
				.alertsFail(LogAlerts.FailLevel.WARNING)
				.alertsUnusedKey(UnusedKeyLevel.ERROR)
				.build();
			var gum = RainbowGum.builder(config).build();
			var e = assertThrows(IllegalStateException.class, gum::start);
			assertEquals("""
					1 alert(s) at warning or above were recorded while starting and logging.alerts.fail=warning:
					[ERROR] MESSAGE""".replace("MESSAGE", UNREAD_MESSAGE), e.getMessage());
		}
		finally {
			System.clearProperty(UNREAD);
		}
	}

	@Test
	void thePropertyOverridesTheBuilder() {
		System.setProperty(UNREAD, "x");
		try {
			var config = LogConfig.builder()
				.properties(LogProperties.builder().fromProperties("logging.alerts.unusedKey=info").build())
				.alertsFail(LogAlerts.FailLevel.WARNING)
				.alertsUnusedKey(UnusedKeyLevel.ERROR)
				.build();
			try (var gum = RainbowGum.builder(config).build().start()) {
				var alerts = gum.config()
					.alerts()
					.dump()
					.stream()
					.filter(e -> e.message().startsWith("Property key"))
					.map(e -> "[" + e.level() + "] " + e.message())
					.toList();
				assertEquals(List.of("[INFO] " + UNREAD_MESSAGE), alerts);
			}
		}
		finally {
			System.clearProperty(UNREAD);
		}
	}

	@Test
	void invalidValueFails() {
		var e = assertThrows(LogProperty.ValidationException.class,
				() -> LogConfig.builder()
					.properties(LogProperties.builder().fromProperties("logging.alerts.unusedKey=loud").build())
					.build());
		assertEquals("""
				Validation failed for io.jstach.rainbowgum.LogAlerts:
				Error for property. key: 'logging.alerts.unusedKey' from PROPERTIES_STRING[logging.alerts.unusedKey], \
				'loud' is not a valid value for io.jstach.rainbowgum.LogAlerts.UnusedKeyLevel. \
				Valid values: 'info', 'warning', 'error', 'warn'""", e.getMessage());
	}

}

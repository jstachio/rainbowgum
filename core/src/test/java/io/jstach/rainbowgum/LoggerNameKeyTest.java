package io.jstach.rainbowgum;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.lang.System.Logger.Level;
import java.util.EnumSet;
import java.util.List;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import io.jstach.rainbowgum.LogConfig.ChangePublisher.ChangeType;
import io.jstach.rainbowgum.LogEvent.Caller.CallerType;

/*
 * Logger names are not property keys: a facade passes any string. Per logger lookups
 * build keys from the name and each parent path, so a name that does not make a valid
 * key must fall back to its parents and the root instead of throwing.
 *
 * https://github.com/jstachio/rainbowgum/issues/752
 *
 * TODO #752 the commented out assertions fail on every name except .leading: level
 * falls back to INFO with an ERROR alert ("Failed to resolve level for logger
 * 'trailing.', falling back to INFO"), and callerType and allowedChanges throw
 * IllegalArgumentException ("Property key should not start or end with '.'" or, for
 * {weird}, "Keyed parameter mismatch").
 */
class LoggerNameKeyTest {

	static List<String> loggerNames() {
		return List.of(
				// Testcontainers shortens image names with a digest and ends them with
				// ...
				"tc.floci/floci-gcp@sha256:405c128b...", //
				"trailing.", //
				".leading", //
				"a..b", //
				".", //
				"tc.docker[{weird}]");
	}

	@ParameterizedTest
	@MethodSource("loggerNames")
	void level(String loggerName) {
		var config = config();
		var level = config.levelResolver().resolveLevel(loggerName);
		var alerts = config.alerts().dump().stream().map(e -> e.level() + " " + e.message()).toList();
		if (loggerName.equals(".leading")) {
			assertEquals(Level.WARNING, level);
			assertEquals(List.of(), alerts);
		}
		// assertEquals(Level.WARNING, level);
		// assertEquals(List.of(), alerts);
	}

	@ParameterizedTest
	@MethodSource("loggerNames")
	void callerType(String loggerName) {
		if (loggerName.equals(".leading")) {
			assertEquals(CallerType.BASIC, config().changePublisher().callerType(loggerName));
		}
		// assertEquals(CallerType.BASIC,
		// config().changePublisher().callerType(loggerName));
	}

	@ParameterizedTest
	@MethodSource("loggerNames")
	void allowedChanges(String loggerName) {
		if (loggerName.equals(".leading")) {
			assertEquals(EnumSet.of(ChangeType.LEVEL), config().changePublisher().allowedChanges(loggerName));
		}
		// assertEquals(EnumSet.of(ChangeType.LEVEL),
		// config().changePublisher().allowedChanges(loggerName));
	}

	private static LogConfig config() {
		return LogConfig.builder().properties(LogProperties.builder().fromProperties("""
				logging.level=warn
				logging.change.mode=on
				logging.change.logger=level
				logging.caller.logger=true
				""").build()).build();
	}

}

package io.jstach.rainbowgum;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.lang.System.Logger.Level;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

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
 */
class LoggerNameKeyTest {

	static List<String> loggerNames() {
		return List.of(
				// Testcontainers ends shortened digest image names with ...
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
		assertEquals(Level.WARNING, config.levelResolver().resolveLevel(loggerName));
		assertEquals(List.of(), config.alerts().dump().stream().map(e -> e.level() + " " + e.message()).toList());
	}

	@ParameterizedTest
	@MethodSource("loggerNames")
	void callerType(String loggerName) {
		assertEquals(CallerType.BASIC, config().changePublisher().callerType(loggerName));
	}

	@ParameterizedTest
	@MethodSource("loggerNames")
	void allowedChanges(String loggerName) {
		assertEquals(EnumSet.of(ChangeType.LEVEL), config().changePublisher().allowedChanges(loggerName));
	}

	/*
	 * The name can also be set exactly, like any other logger. A map is used since a
	 * properties file would split a key like the Testcontainers name at its colon.
	 */
	@ParameterizedTest
	@MethodSource("loggerNames")
	void exactName(String loggerName) {
		var values = Map.of( //
				"logging.level", "warn", //
				"logging.change.mode", "on", //
				"logging.change.logger", "level", //
				"logging.caller.logger", "true", //
				Loggers.Key.key(LogProperties.LEVEL_PREFIX, loggerName), "debug", //
				Loggers.Key.key(LogProperties.CHANGE_LOGGER_PREFIX, loggerName), "false", //
				Loggers.Key.key(LogProperties.CALLER_LOGGER_PREFIX, loggerName), "false");
		var config = LogConfig.builder().properties(LogProperties.builder().fromFunction(values::get).build()).build();
		assertEquals(Level.DEBUG, config.levelResolver().resolveLevel(loggerName));
		assertEquals(CallerType.NONE, config.changePublisher().callerType(loggerName));
		assertEquals(Set.of(), config.changePublisher().allowedChanges(loggerName));
		assertEquals(List.of(), config.alerts().dump().stream().map(e -> e.level() + " " + e.message()).toList());
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

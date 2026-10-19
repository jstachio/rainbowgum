package io.jstach.rainbowgum;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.lang.System.Logger.Level;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import io.jstach.rainbowgum.LogConfig.ChangePublisher.ChangeType;
import io.jstach.rainbowgum.LogEvent.Caller.CallerType;

/*
 * logging.level.root is the root logger, like logging.level, for every key that ends
 * with a logger name.
 */
class RootLoggerAliasTest {

	private static LogProperties properties(int order, String properties) {
		return LogProperties.builder().fromProperties(properties).order(order).build();
	}

	private static LogConfig config(LogProperties... properties) {
		return LogConfig.builder().properties(LogProperties.of(List.of(properties))).build();
	}

	@Test
	void aliasSetsTheRootLevel() {
		var levels = config(properties(0, "logging.level.root=debug")).levelResolver();
		assertEquals(Level.DEBUG, levels.resolveLevel("com.example"));
		assertEquals(Level.DEBUG, levels.defaultLevel());
	}

	@Test
	void aliasSetsTheRootChangeAndCaller() {
		var changes = config(properties(0, """
				logging.change.mode=on
				logging.change.logger.root=level
				logging.caller.logger.root=true
				logging.change.logger.com.example=false
				""")).changePublisher();
		assertEquals(EnumSet.of(ChangeType.LEVEL), changes.allowedChanges("other"));
		assertEquals(Set.of(), changes.allowedChanges("com.example.Foo"));
		assertEquals(CallerType.BASIC, changes.callerType("other"));
	}

	@Test
	void aliasSetsTheRouteRootLevel() {
		var levels = ConfigLevelResolver.of(properties(0, "logging.route.console.level.root=error"),
				"logging.route.console.level");
		assertEquals(Level.ERROR, levels.levelOrNull(""));
	}

	/*
	 * The higher priority source wins whichever of the two keys each source uses.
	 */
	@ParameterizedTest
	@CsvSource(delimiter = '|', textBlock = """
			logging.level.root=debug | logging.level=info       | DEBUG
			logging.level=warn       | logging.level.root=debug | WARNING
			logging.level=warn       | logging.level=info       | WARNING
			logging.level.root=error | logging.level.root=info  | ERROR
			""")
	void higherPrioritySourceWins(String high, String low, Level expected) {
		var levels = config(properties(400, high), properties(100, low)).levelResolver();
		assertEquals(expected, levels.resolveLevel("com.example"));
	}

	@Test
	void plainKeyWinsInTheSameSource() {
		var levels = config(properties(0, """
				logging.level=warn
				logging.level.root=debug
				""")).levelResolver();
		assertEquals(Level.WARNING, levels.resolveLevel("com.example"));
	}

	/*
	 * A logger named root is the root logger as far as levels are concerned, the same as
	 * Spring Boot.
	 */
	@Test
	void loggerNamedRootUsesTheRootLevel() {
		var levels = config(properties(0, """
				logging.level=warn
				logging.level.root.child=debug
				""")).levelResolver();
		assertEquals(Level.WARNING, levels.resolveLevel("root"));
		assertEquals(Level.DEBUG, levels.resolveLevel("root.child"));
	}

}

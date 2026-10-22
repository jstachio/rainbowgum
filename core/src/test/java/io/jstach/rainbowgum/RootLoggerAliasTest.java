package io.jstach.rainbowgum;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.lang.System.Logger.Level;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

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
		for (String name : List.of("", "com.example", "root", "root.child")) {
			assertEquals(expected, levels.resolveLevel(name), name);
		}
	}

	@Test
	void plainKeyWinsInTheSameSource() {
		var levels = config(properties(0, """
				logging.level=warn
				logging.level.root=debug
				""")).levelResolver();
		for (String name : List.of("", "com.example", "root", "root.child")) {
			assertEquals(Level.WARNING, levels.resolveLevel(name), name);
		}
	}

	/*
	 * The root alias supplies the default; more specific logger settings still win.
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

	@Test
	void literalRootUsesTheRootChangeAndCallerPrecedence() {
		var changes = config(properties(0, """
				logging.change.mode=on
				logging.change.logger=false
				logging.change.logger.root=level
				logging.caller.logger=false
				logging.caller.logger.root=true
				""")).changePublisher();
		for (String name : List.of("", "other", "root", "root.child")) {
			assertEquals(Set.of(), changes.allowedChanges(name), name);
			assertEquals(CallerType.NONE, changes.callerType(name), name);
		}
	}

	@ParameterizedTest
	@ValueSource(strings = { "", ".root" })
	void groupCanTargetLiteralRoot(String rootSuffix) {
		var config = config(properties(0, """
				logging.level%s=warn
				logging.change.mode=on
				logging.change.logger%s=false
				logging.caller.logger%s=false
				logging.groups=literalRoot
				logging.group.literalRoot=root
				logging.level.literalRoot=debug
				logging.change.logger.literalRoot=level
				logging.caller.logger.literalRoot=true
				logging.level.root.direct=error
				logging.change.logger.root.direct=false
				logging.caller.logger.root.direct=false
				""".formatted(rootSuffix, rootSuffix, rootSuffix)));
		var levels = config.levelResolver();
		var changes = config.changePublisher();
		for (String name : List.of("root", "root.child")) {
			assertEquals(Level.DEBUG, levels.resolveLevel(name), name);
			assertEquals(EnumSet.of(ChangeType.LEVEL), changes.allowedChanges(name), name);
			assertEquals(CallerType.BASIC, changes.callerType(name), name);
		}
		for (String name : List.of("", "other")) {
			assertEquals(Level.WARNING, levels.resolveLevel(name), name);
			assertEquals(Set.of(), changes.allowedChanges(name), name);
			assertEquals(CallerType.NONE, changes.callerType(name), name);
		}
		assertEquals(Level.ERROR, levels.resolveLevel("root.direct.Child"));
		assertEquals(Set.of(), changes.allowedChanges("root.direct.Child"));
		assertEquals(CallerType.NONE, changes.callerType("root.direct.Child"));
	}

	@ParameterizedTest
	@ValueSource(strings = { "", ".root" })
	void groupCanTargetLiteralRootInRoute(String rootSuffix) {
		var properties = properties(0, """
				logging.route.console.level%s=warn
				logging.groups=literalRoot
				logging.group.literalRoot=root
				logging.route.console.level.literalRoot=debug
				logging.route.console.level.root.direct=error
				""".formatted(rootSuffix));
		var levels = LevelResolver.builder()
			.config(properties, "logging.route.console.level")
			.build()
			.provide("console", config(properties));
		assertEquals(Level.DEBUG, levels.resolveLevel("root"));
		assertEquals(Level.DEBUG, levels.resolveLevel("root.child"));
		assertEquals(Level.WARNING, levels.resolveLevel("other"));
		assertEquals(Level.ERROR, levels.resolveLevel("root.direct.Child"));
	}

}

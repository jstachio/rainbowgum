package io.jstach.rainbowgum;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.lang.System.Logger.Level;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.MethodSource;

import io.jstach.rainbowgum.LevelResolver.LevelConfig;
import io.jstach.rainbowgum.LogFormatter.LevelFormatter;
import io.jstach.rainbowgum.LogRouter.RouteFlag;
import io.jstach.rainbowgum.output.ListLogOutput;

class LevelResolverTest {

	@Test
	void testBuilder() {
		LevelResolver.builder().level(Level.OFF);
	}

	@ParameterizedTest
	@MethodSource("levels")
	void testSingleRouterLevels(Level level, Level loggerLevel) {
		var props = LogProperties.MutableLogProperties.builder()
			.description("test_props")
			.build()
			.put("logging.global.change", "true")
			.put("logging.level.com.stuff", loggerLevel.toString())
			.put("logging.groups", "sql")
			.put("logging.group.sql", "com.sql")
			.put("logging.level.sql", loggerLevel.toString());
		LogConfig config = LogConfig.builder().properties(props).build();
		ListLogOutput output = new ListLogOutput();
		var gum = RainbowGum.builder(config).route(r -> {
			r.appender("list", a -> {
				a.output(output);
				a.formatter(LogFormatter.builder() //
					.level()
					.space()
					.loggerName()
					.space()
					.message()
					.newline() //
					.build());
			});
		}).build();
		try (var g = gum) {

			var lr = g.router().levelResolver();

			{
				String actual = reformatToString(lr.toString());
				String expected = """
						CachedLevelResolver[
							PriorityLevelResolver[
								CachedLevelResolver[
									PriorityLevelResolver[
										CompositeLevelConfig[
											ConfigLevelResolver[
												prefix=logging.route.default.level,
												properties=MapLogProperties[
													description='test_props',
													order=0
												]
											],
											GroupLevelResolver[
												groupLevelPrefix=logging.route.default.level,
												properties=MapLogProperties[
													description='test_props',
													order=0
												]
											]
										],
										CompositeLevelConfig[
											ConfigLevelResolver[
												prefix=logging.level,
												properties=MapLogProperties[
													description='test_props',
													order=0
												]
											],
											GroupLevelResolver[
												groupLevelPrefix=logging.level,
												properties=MapLogProperties[
													description='test_props',
													order=0
												]
											]
										]
									]
								],
								INFO
							]
						]""";
				assertEquals(expected, actual);
			}
			g.router().eventBuilder("com.stuff.foo", level).message("hello").log();
			assertNotEquals(Level.ALL, config.levelResolver().resolveLevel("com.stuff.foo"));
			props.put(LogProperties.LEVEL_PREFIX, "OFF");
			props.put(LogProperties.concatKey(LogProperties.LEVEL_PREFIX, "com.stuff.foo"), "OFF");
			assertEquals(Level.OFF, config.levelResolver().defaultLevel());

			/*
			 * We check our group level settings
			 */
			assertEquals(LevelResolver.normalizeLevel(loggerLevel),
					config.levelResolver().resolveLevel("com.sql.blah"));

			/*
			 * Assuming the level is enabled we expect the next statement per the level
			 * resolver cache.
			 */
			g.router().eventBuilder("com.stuff.foo", level).message("hello").log();
			{
				checkOutput("list", level, loggerLevel, output);
			}
			output.clear();
			config.changePublisher().publish();
			/*
			 * We expect no output as our level resolving cache is cleared and should
			 * resolve OFF.
			 */
			g.router().eventBuilder("com.stuff.foo", level).message("hello").log();
			assertEquals("", output.toString());

		}
	}

	@ParameterizedTest
	@MethodSource("routeLevels")
	void testMultipleRouterLevels(Level level, Level loggerLevel, Level routeLevel) {
		var props = LogProperties.MutableLogProperties.builder()
			.description("test_props")
			.build()
			.put("logging.global.change", "true")
			.put("logging.level.com.stuff", loggerLevel.toString());
		LogConfig config = LogConfig.builder().properties(props).build();
		ListLogOutput first = new ListLogOutput();
		ListLogOutput second = new ListLogOutput();

		var gum = RainbowGum.builder(config).route(r -> {
			r.appender("first", a -> {
				a.output(first);
				a.formatter(LogFormatter.builder() //
					.level()
					.space()
					.loggerName()
					.space()
					.message()
					.newline() //
					.build());
			});
		}).route("second", r -> {
			r.flag(RouteFlag.IGNORE_GLOBAL_LEVEL_RESOLVER);
			r.level(routeLevel);
			r.appender("second", a -> {
				a.output(second);
				a.formatter(LogFormatter.builder() //
					.level()
					.space()
					.loggerName()
					.space()
					.message()
					.newline() //
					.build());
			});
		}).build();

		try (var g = gum) {

			var lr = g.router().levelResolver();

			{
				String actual = reformatToString(lr.toString());
				String expected = """
						CachedLevelResolver[
							CompositeLevelResolver[
								CachedLevelResolver[
									PriorityLevelResolver[
										CachedLevelResolver[
											PriorityLevelResolver[
												CompositeLevelConfig[
													ConfigLevelResolver[
														prefix=logging.route.default.level,
														properties=MapLogProperties[
															description='test_props',
															order=0
														]
													],
													GroupLevelResolver[
														groupLevelPrefix=logging.route.default.level,
														properties=MapLogProperties[
															description='test_props',
															order=0
														]
													]
												],
												CompositeLevelConfig[
													ConfigLevelResolver[
														prefix=logging.level,
														properties=MapLogProperties[
															description='test_props',
															order=0
														]
													],
													GroupLevelResolver[
														groupLevelPrefix=logging.level,
														properties=MapLogProperties[
															description='test_props',
															order=0
														]
													]
												]
											]
										],
										INFO
									]
								],
								CachedLevelResolver[
									CompositeLevelConfig[
										%s,
										ConfigLevelResolver[
											prefix=logging.route.second.level,
											properties=MapLogProperties[
												description='test_props',
												order=0
											]
										],
										GroupLevelResolver[
											groupLevelPrefix=logging.route.second.level,
											properties=MapLogProperties[
												description='test_props',
												order=0
											]
										]
									]
								]
							]
						]""".formatted(LevelResolver.normalizeLevel(routeLevel));
				assertEquals(expected, actual);
			}
			g.router().eventBuilder("com.stuff.foo", level).message("hello").log();
			assertNotEquals(Level.ALL, config.levelResolver().resolveLevel("com.stuff.foo"));
			props.put(LogProperties.LEVEL_PREFIX, "OFF");
			props.put(LogProperties.concatKey(LogProperties.LEVEL_PREFIX, "com.stuff.foo"), "OFF");
			assertEquals(Level.OFF, config.levelResolver().defaultLevel());
			// assertNotEquals(Level.OFF,
			// g.router().levelResolver().resolveLevel("com.stuff.foo"));

			/*
			 * Assuming the level is enabled we expect the next statement per the level
			 * resolver cache.
			 */
			g.router().eventBuilder("com.stuff.foo", level).message("hello").log();
			{
				checkOutput("first", level, loggerLevel, first);
				checkOutput("second", level, routeLevel, second);
			}
			first.clear();
			config.changePublisher().publish();
			/*
			 * We expect no output as our level resolving cache is cleared and should
			 * resolve OFF.
			 */
			g.router().eventBuilder("com.stuff.foo", level).message("hello").log();
			assertEquals("", first.toString());

		}
	}

	private void checkOutput(String name, Level level, Level loggerLevel, ListLogOutput output) {
		String actual = output.toString();
		String expected;
		if (LevelResolver.checkEnabled(level, loggerLevel)) {
			expected = """
					%s com.stuff.foo hello
					%s com.stuff.foo hello
					""".formatted(LevelFormatter.toString(level), LevelFormatter.toString(level));
		}
		else {
			expected = "";
		}
		assertEquals(expected, actual, name);
	}

	public static String reformatToString(String inputString) {
		StringBuilder result = new StringBuilder();
		int indentationLevel = 0;

		boolean skip = false;
		for (int i = 0; i < inputString.length(); i++) {
			char c = inputString.charAt(i);
			if (skip) {
				skip = false;
				if (c == ' ') {
					continue;
				}
			}
			if (c == '[') {
				indentationLevel++;
				result.append(c).append('\n').append("\t".repeat(indentationLevel));
			}
			else if (c == ']') {
				indentationLevel--;
				result.append('\n').append("\t".repeat(indentationLevel)).append(c);
			}
			else if (c == ',') {
				result.append(c).append('\n').append("\t".repeat(indentationLevel));
				skip = true;
			}
			else {
				result.append(c);
			}
		}

		return result.toString();
	}

	private static Stream<Arguments> levels() {
		return EnumCombinations.args(Level.class, Level.class);
	}

	private static Stream<Arguments> routeLevels() {
		return EnumCombinations.args(Level.class, Level.class, Level.class);
	}

	@Test
	void testOffReturnsStaticOffResolver() {
		assertSame(StaticLevelResolver.OFF, LevelResolver.off());
	}

	@ParameterizedTest
	@CsvSource({ "ALL, TRACE", "all, TRACE", "TRACE, TRACE", "FINEST, TRACE", "DEBUG, DEBUG", "FINE, DEBUG",
			"INFO, INFO", "WARN, WARNING", "WARNING, WARNING", "ERROR, ERROR", "SEVERE, ERROR", "OFF, OFF", })
	void testParseLevelAliasesAndCaseInsensitivity(String input, Level expected) {
		assertEquals(expected, LevelResolver.parseLevel(input));
	}

	@Test
	void testParseLevelRejectsUnknownInput() {
		assertThrows(IllegalArgumentException.class, () -> LevelResolver.parseLevel("bogus"));
	}

	@Test
	void testLevelConfigOfEmptyCollectionIsOff() {
		assertSame(StaticLevelResolver.OFF, LevelConfig.of(List.of()));
	}

	/*
	 * SingleLevelResolver constructed directly with ALL - the public ofStaticMap path
	 * always normalizes ALL to TRACE first via allToTrace, so this is the only way to
	 * exercise LevelConfig's private allToNull(ALL) branch (as opposed to
	 * allToNull(null), already covered by every other up-path search miss in this file).
	 */
	@Test
	void testUpPathSearchTreatsAllLevelAsNoOpinion() {
		LevelConfig resolver = new SingleLevelResolver("foo", Level.ALL);
		assertEquals(Level.ALL, resolver.resolveLevel("foo"));
	}

	@Test
	void testBuilderOfResolversWithNoResolversIsAll() {
		assertSame(StaticLevelResolver.ALL, LevelResolver.Builder.ofResolvers(List.of()));
	}

	@Test
	void testBuilderOfStaticMapWithNoLevelsIsAll() {
		assertSame(StaticLevelResolver.ALL, LevelResolver.Builder.ofStaticMap(Map.of()));
	}

	@Test
	void testCompositeLevelResolverFlattensNestedComposites() {
		var inner = CompositeLevelResolver.of(List.of(StaticLevelResolver.DEBUG, StaticLevelResolver.INFO));
		var outer = (CompositeLevelResolver) CompositeLevelResolver.of(List.of(inner, StaticLevelResolver.ERROR));
		assertEquals(3, outer.resolvers().length);
	}

	@Test
	void testCompositeLevelResolverFallsBackToDefaultLevelWhenEveryResolverIsAll() {
		var resolver = new CompositeLevelResolver(new LevelResolver[] { StaticLevelResolver.ALL }, Level.WARNING);
		assertEquals(Level.WARNING, resolver.resolveLevel("anything"));
	}

	@Test
	void testCompositeLevelResolverClearCascadesToEveryResolver() {
		int[] clearCount = new int[2];
		LevelResolver a = new LevelResolver() {
			@Override
			public Level resolveLevel(String name) {
				return Level.ALL;
			}

			@Override
			public void clear() {
				clearCount[0]++;
			}
		};
		LevelResolver b = new LevelResolver() {
			@Override
			public Level resolveLevel(String name) {
				return Level.ALL;
			}

			@Override
			public void clear() {
				clearCount[1]++;
			}
		};
		var resolver = CompositeLevelResolver.of(List.of(a, b));
		resolver.clear();
		assertEquals(1, clearCount[0]);
		assertEquals(1, clearCount[1]);
	}

	@Test
	void testPriorityLevelResolverFlattensNestedPriorityResolvers() {
		var inner = (PriorityLevelResolver) PriorityLevelResolver
			.of(List.of(StaticLevelResolver.ALL, StaticLevelResolver.INFO));
		var outer = (PriorityLevelResolver) PriorityLevelResolver.of(List.of(inner, StaticLevelResolver.ERROR));
		assertEquals(3, outer.resolvers().length);
	}

	@Test
	void testPriorityLevelResolverOfEmptyIsAll() {
		assertSame(StaticLevelResolver.ALL, PriorityLevelResolver.of(List.of()));
	}

	@Test
	void testPriorityLevelResolverOfSingleReturnsThatResolverUnwrapped() {
		var resolver = LevelResolver.off();
		assertSame(resolver, PriorityLevelResolver.of(List.of(resolver)));
	}

	@Test
	void testGroupLevelResolverResolveLevelDelegatesDirectly() {
		var props = LogProperties.MutableLogProperties.builder()
			.build()
			.put("logging.groups", "sql")
			.put("logging.group.sql", "com.sql")
			.put("logging.level.sql", "DEBUG");
		var resolver = GroupLevelResolver.of(props);
		assertEquals(Level.DEBUG, resolver.resolveLevel("com.sql.blah"));
	}

	@Test
	void testGroupLevelResolverClearRepopulatesFromCurrentProperties() {
		var props = LogProperties.MutableLogProperties.builder()
			.build()
			.put("logging.groups", "sql")
			.put("logging.group.sql", "com.sql")
			.put("logging.level.sql", "DEBUG");
		var resolver = GroupLevelResolver.of(props);
		assertEquals(Level.DEBUG, resolver.resolveLevel("com.sql.blah"));
		props.put("logging.level.sql", "ERROR");
		resolver.clear();
		assertEquals(Level.ERROR, resolver.resolveLevel("com.sql.blah"));
	}

	/*
	 * A group with no loggers (an empty logging.group.<name> value parses to an empty
	 * list) is filtered out before it ever gets a chance to contribute a level - its
	 * logging.level.<name> setting has nothing to attach to and must not surface for any
	 * logger name.
	 */
	@Test
	void testGroupLevelResolverIgnoresGroupWithNoLoggers() {
		var props = LogProperties.MutableLogProperties.builder()
			.build()
			.put("logging.groups", "empty")
			.put("logging.group.empty", "")
			.put("logging.level.empty", "DEBUG");
		var resolver = GroupLevelResolver.of(props);
		assertEquals(Level.ALL, resolver.resolveLevel("com.anything"));
	}

	/*
	 * testMultipleRouterLevels above only ever sets IGNORE_GLOBAL_LEVEL_RESOLVER via the
	 * programmatic route builder's r.flag(RouteFlag...) - RouteFlag.parse(String)/
	 * parse(Collection<String>), the code path a logging.route.<name>.flags property
	 * value actually goes through, had no coverage anywhere.
	 */
	@Test
	void testIgnoreGlobalLevelResolverSetViaProperty() {
		var props = LogProperties.MutableLogProperties.builder()
			.description("test_props")
			.build()
			.put("logging.level.com.stuff", "ERROR")
			.put("logging.route.second.flags", "IGNORE_GLOBAL_LEVEL_RESOLVER");
		LogConfig config = LogConfig.builder().properties(props).build();
		var defaultOutput = new ListLogOutput();
		var secondOutput = new ListLogOutput();
		var gum = RainbowGum.builder(config).route(r -> {
			r.appender("default",
					a -> a.output(defaultOutput).encoder(LogFormatter.builder().message().newline().encoder()));
		}).route("second", r -> {
			r.appender("second",
					a -> a.output(secondOutput).encoder(LogFormatter.builder().message().newline().encoder()));
		}).build();
		try (var g = gum) {
			g.router().eventBuilder("com.stuff", Level.INFO).message("hello").log();
		}
		// The default route has no IGNORE_GLOBAL_LEVEL_RESOLVER flag, so it falls back
		// to the global logging.level.com.stuff=ERROR and filters INFO out.
		assertEquals("", defaultOutput.toString());
		// The "second" route's logging.route.second.flags=IGNORE_GLOBAL_LEVEL_RESOLVER
		// property makes it ignore the global resolver entirely - with nothing else
		// configured for it, it resolves to Level.ALL (normalized to TRACE), so
		// everything is enabled.
		assertEquals("hello\n", secondOutput.toString());
	}

	/*
	 * The above test only ever calls RouteFlag.parse(Collection) with a non-empty
	 * collection (the property parses "IGNORE_GLOBAL_LEVEL_RESOLVER" into a one-element
	 * list) - a route with no logging.route.<name>.flags property configured never
	 * reaches parse(Collection) at all (the PropertyGetter is MISSING and its map()
	 * short-circuits before calling the mapper), so the isEmpty() early return is only
	 * reachable if the property is present but parses to zero elements.
	 */
	@Test
	void testRouteFlagParseCollectionOfEmptyIsEmptySet() {
		assertEquals(EnumSet.noneOf(RouteFlag.class), RouteFlag.parse(List.of()));
	}

}

package io.jstach.rainbowgum;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

import java.lang.System.Logger.Level;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

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
			.put("logging.level.com.stuff", loggerLevel.toString());
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
							CompositeLevelConfig[
								ConfigLevelResolver[
									prefix=logging.route.default.level,
									properties=MapLogProperties[
										description='test_props',
										order=0
									]
								],
								ConfigLevelResolver[
									prefix=logging.level,
									properties=MapLogProperties[
										description='test_props',
										order=0
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
									CompositeLevelConfig[
										ConfigLevelResolver[
											prefix=logging.route.default.level,
											properties=MapLogProperties[
												description='test_props',
												order=0
											]
										],
										ConfigLevelResolver[
											prefix=logging.level,
											properties=MapLogProperties[
												description='test_props',
												order=0
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

}

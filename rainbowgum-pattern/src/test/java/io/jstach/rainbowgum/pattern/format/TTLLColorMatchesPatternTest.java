package io.jstach.rainbowgum.pattern.format;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.lang.System.Logger.Level;
import java.time.Instant;
import java.time.ZoneOffset;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import io.jstach.rainbowgum.KeyValues;
import io.jstach.rainbowgum.LogEvent;
import io.jstach.rainbowgum.LogFormatter;
import io.jstach.rainbowgum.format.TTLL;
import io.jstach.rainbowgum.format.TTLLFormatterBuilder;

/*
 * Each TTLL color theme must produce exactly what its equivalent pattern produces, so
 * console output looks the same with or without the pattern module.
 */
class TTLLColorMatchesPatternTest {

	@ParameterizedTest
	@EnumSource(value = Level.class, names = { "TRACE", "DEBUG", "INFO", "WARNING", "ERROR" })
	void rainbowgumThemeMatchesTheDefaultPattern(Level level) {
		assertThemeMatchesPattern(TTLL.ColorTheme.RAINBOWGUM, PatternConfigurator.DEFAULT_PATTERN, level);
	}

	/*
	 * Spring Boot's console pattern colors, reduced to the TTLL parts.
	 */
	@ParameterizedTest
	@EnumSource(value = Level.class, names = { "TRACE", "DEBUG", "INFO", "WARNING", "ERROR" })
	void springThemeMatchesSpringBootColors(Level level) {
		assertThemeMatchesPattern(TTLL.ColorTheme.SPRING,
				"%clr(%date{HH:mm:ss.SSS}){faint} %clr([%thread]){faint} %clr(%-5level){} %clr(%logger){cyan} - %msg%n%ex",
				level);
	}

	private static void assertThemeMatchesPattern(TTLL.ColorTheme theme, String patternString, Level level) {
		var event = LogEvent.of(Instant.parse("2026-10-05T12:00:00.123Z"), "main", 7, level, "com.example.App",
				"hello world", KeyValues.of(), null);
		var config = PatternConfig.builder("test").ansiDisabled(false).zoneId(ZoneOffset.UTC).build();
		LogFormatter pattern = PatternCompiler.builder().patternConfig(config).build().compile(patternString);
		LogFormatter ttll = new TTLLFormatterBuilder("test").color(TTLL.ColorMode.FORCE).theme(theme).build();
		var expected = new StringBuilder();
		pattern.format(expected, event);
		var actual = new StringBuilder();
		ttll.format(actual, event);
		assertEquals(expected.toString(), actual.toString());
	}

}

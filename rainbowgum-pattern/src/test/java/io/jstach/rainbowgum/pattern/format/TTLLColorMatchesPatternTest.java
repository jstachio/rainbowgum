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
 * The TTLL rainbowgum color theme must produce exactly what the pattern encoder's
 * default colored pattern produces, so console output looks the same with or without
 * the pattern module.
 */
class TTLLColorMatchesPatternTest {

	@ParameterizedTest
	@EnumSource(value = Level.class, names = { "TRACE", "DEBUG", "INFO", "WARNING", "ERROR" })
	void rainbowgumThemeMatchesTheDefaultPattern(Level level) {
		var event = LogEvent.of(Instant.parse("2026-10-05T12:00:00.123Z"), "main", 7, level, "com.example.App",
				"hello world", KeyValues.of(), null);
		var config = PatternConfig.builder("test").ansiDisabled(false).zoneId(ZoneOffset.UTC).build();
		LogFormatter pattern = PatternCompiler.builder()
			.patternConfig(config)
			.build()
			.compile(PatternConfigurator.DEFAULT_PATTERN);
		LogFormatter ttll = new TTLLFormatterBuilder("test").color(TTLL.ColorTheme.RAINBOWGUM).build();
		var expected = new StringBuilder();
		pattern.format(expected, event);
		var actual = new StringBuilder();
		ttll.format(actual, event);
		assertEquals(expected.toString(), actual.toString());
	}

}

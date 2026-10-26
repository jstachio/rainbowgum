package io.jstach.rainbowgum.pattern.format;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.lang.System.Logger.Level;
import java.time.Instant;
import java.util.Locale;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import io.jstach.rainbowgum.KeyValues;
import io.jstach.rainbowgum.LogConfig;
import io.jstach.rainbowgum.LogConfig.Globals.AnsiMode;
import io.jstach.rainbowgum.LogEvent;
import io.jstach.rainbowgum.LogProperties;
import io.jstach.rainbowgum.format.AnsiSupport;

class PatternAnsiModeTest {

	static final String RED = "\u001B[31mx\u001B[0;39m";

	static String format(String properties) {
		var config = LogConfig.builder()
			.properties(LogProperties.builder().fromProperties(properties).build())
			.configurator(new PatternConfigurator())
			.build();
		var formatter = PatternCompiler.of(b -> {
		}).provide("default", config).compile("%red(x)");
		var event = LogEvent.of(Instant.EPOCH, "main", 1, Level.INFO, "test", "hello", KeyValues.of(), null);
		var sb = new StringBuilder();
		formatter.format(sb, event);
		return sb.toString();
	}

	/*
	 * A global off wins, a pattern auto follows the global mode, and otherwise the
	 * pattern decides.
	 */
	@ParameterizedTest
	@CsvSource({ //
			"off,  off,  plain", //
			"off,  auto, plain", //
			"off,  on,   plain", //
			"on,   off,  plain", //
			"on,   auto, red", //
			"on,   on,   red", //
			"auto, off,  plain", //
			"auto, auto, console", //
			"auto, on,   red", //
	})
	void patternAnsiCombinesWithTheGlobalMode(String global, String pattern, String expected) {
		String actual = format("""
				logging.global.ansi.mode=%s
				logging.pattern.config.default.ansi=%s
				""".formatted(global, pattern));
		String red = switch (expected) {
			case "red" -> RED;
			case "plain" -> "x";
			default -> AnsiSupport.isAnsiSupported() ? RED : "x";
		};
		assertEquals(red, actual);
	}

	@ParameterizedTest
	@CsvSource({ "true, ON", "false, OFF", "Auto, AUTO", " on , ON" })
	void ansiTakesTheGlobalModeValues(String value, AnsiMode expected) {
		var config = PatternConfig.builder("test")
			.fromProperties(LogProperties.builder()
				.fromProperties("logging.pattern.config.test.ansi=" + value.toLowerCase(Locale.ROOT))
				.build())
			.build();
		assertEquals(expected, config.ansi());
	}

	@Test
	void invalidAnsiValueIsReported() {
		var properties = LogProperties.builder().fromProperties("logging.pattern.config.test.ansi=sometimes").build();
		var e = assertThrows(RuntimeException.class, () -> PatternConfig.builder("test").fromProperties(properties));
		String expected = """
				Validation failed for io.jstach.rainbowgum.pattern.format.PatternConfigBuilder:
				Error for property. key: 'logging.pattern.config.test.ansi' from PROPERTIES_STRING[logging.pattern.config.test.ansi], \
				'sometimes' is not a valid value for io.jstach.rainbowgum.LogConfig.Globals.AnsiMode. \
				Valid values: 'auto', 'on', 'off', 'true', 'false'""";
		assertEquals(expected, e.getMessage());
	}

	@Test
	void defaultIsAutoAndUniversalIsOff() {
		assertEquals(AnsiMode.AUTO, PatternConfig.of().ansi());
		assertEquals(AnsiMode.OFF, PatternConfig.ofUniversal().ansi());
		assertEquals(AnsiMode.AUTO, PatternConfig.builder("test").build().ansi());
	}

}

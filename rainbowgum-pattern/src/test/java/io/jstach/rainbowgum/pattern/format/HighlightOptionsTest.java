package io.jstach.rainbowgum.pattern.format;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.lang.System.Logger.Level;
import java.time.Instant;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import io.jstach.rainbowgum.KeyValues;
import io.jstach.rainbowgum.LogEvent;

/*
 * %highlight with no options uses Logback's colors. Options are Log4j2 style LEVEL=style
 * pairs, a Rainbow Gum extension Logback would ignore.
 */
class HighlightOptionsTest {

	static final String E = "\033[";

	private static String format(String pattern, Level level) {
		var config = PatternConfig.builder("test").ansiDisabled(false).build();
		var formatter = PatternCompiler.builder().patternConfig(config).build().compile(pattern);
		var event = LogEvent.of(Instant.EPOCH, "main", 1, level, "test", "x", KeyValues.of(), null);
		var sb = new StringBuilder();
		formatter.format(sb, event);
		return sb.toString();
	}

	@ParameterizedTest
	@CsvSource({ "ERROR,1;31", "WARNING,31", "INFO,34", "DEBUG,39", "TRACE,39" })
	void noOptionsUseLogbackColors(Level level, String code) {
		assertEquals(E + code + "mL" + E + "0;39m", format("%highlight(L)", level));
	}

	@ParameterizedTest
	@CsvSource(delimiter = '|', value = { //
			"ERROR|1;31", // from the option
			"WARNING|1;33", // WARNING is accepted as WARN
			"INFO|1;34", //
			"DEBUG|2;39", // faint alone keeps the default color
			"TRACE|91" // bright color
	})
	void optionsSetTheColorPerLevel(Level level, String code) {
		String pattern = "%highlight(L){ERROR=red bold, WARNING=bold yellow, INFO=blue bold, DEBUG=faint, TRACE=bright_red}";
		assertEquals(E + code + "mL" + E + "0;39m", format(pattern, level));
	}

	@ParameterizedTest
	@CsvSource({ "WARNING,31", "DEBUG,39" })
	void levelsNotListedKeepTheirDefault(Level level, String code) {
		assertEquals(E + code + "mL" + E + "0;39m", format("%highlight(L){ERROR=red bold, INFO=blue bold}", level));
	}

	@ParameterizedTest
	@CsvSource(delimiter = '|', value = { //
			"%highlight(L){red}|highlight option 'red' should be LEVEL=style, for example ERROR=red bold", //
			"%highlight(L){FATAL=red}|highlight option 'FATAL=red' has an unknown level. Valid levels: ERROR, WARN, INFO, DEBUG, TRACE", //
			"%highlight(L){ERROR=red blue}|highlight style 'red blue' has more than one color", //
			"%highlight(L){ERROR=pink}|highlight style 'pink' has an unknown word 'pink'. Use a color (black, red, green, yellow, blue, magenta, cyan, white, default, or bright_ variants) and optionally bold or faint" })
	void invalidOptionsFail(String pattern, String message) {
		var e = assertThrows(IllegalArgumentException.class, () -> format(pattern, Level.INFO));
		assertEquals(message, e.getMessage());
	}

}

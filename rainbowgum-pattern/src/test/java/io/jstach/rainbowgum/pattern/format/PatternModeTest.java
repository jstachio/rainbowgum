package io.jstach.rainbowgum.pattern.format;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.Locale;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import io.jstach.rainbowgum.LogConfig;
import io.jstach.rainbowgum.LogProperties;
import io.jstach.rainbowgum.LogReporter;
import io.jstach.rainbowgum.RainbowGum;

class PatternModeTest {

	static LogConfig config(String properties) {
		return LogConfig.builder()
			.properties(LogProperties.builder().fromProperties(properties).build())
			.configurator(new PatternConfigurator())
			.build();
	}

	/*
	 * The encoder of the default console appender, which names no encoder.
	 */
	static String consoleEncoder(String properties) {
		var config = config(properties);
		try (var gum = RainbowGum.builder(config).build()) {
			return config.reporter()
				.report(LogReporter.Options.of(LogReporter.Section.COMPONENTS))
				.lines()
				.filter(line -> line.strip().startsWith("Encoder:"))
				.findFirst()
				.orElseThrow()
				.strip();
		}
	}

	@ParameterizedTest
	@ValueSource(strings = { "", "logging.pattern.mode=console", "logging.pattern.mode=explicit",
			"logging.pattern.mode= Explicit " })
	void modeDecidesTheConsoleEncoder(String properties) {
		String expected = properties.toLowerCase(Locale.ROOT).contains("explicit")
				? "Encoder: FormatterEncoder (contentType=text/plain; charset=UTF-8, description=\"ttll color=default theme=rainbowgum colored=false\")"
				: "Encoder: FormatterEncoder (contentType=text/plain; charset=UTF-8, description=\""
						+ PatternConfigurator.DEFAULT_PATTERN + "\")";
		assertEquals(expected, consoleEncoder(properties));
	}

	@Test
	void invalidModeIsReported() {
		var e = assertThrows(RuntimeException.class, () -> config("logging.pattern.mode=true"));
		String expected = """
				Validation failed for io.jstach.rainbowgum.pattern.format.PatternConfigurator:
				Error for property. key: 'logging.pattern.mode' from PROPERTIES_STRING[logging.pattern.mode], \
				'true' is not a valid value for io.jstach.rainbowgum.pattern.format.PatternConfigurator.Mode. \
				Valid values: 'console', 'explicit'""";
		assertEquals(expected, e.getMessage());
	}

}

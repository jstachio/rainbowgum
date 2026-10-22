package io.jstach.rainbowgum;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.jspecify.annotations.Nullable;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.MethodSource;

class LoggerKeyTest {

	@ParameterizedTest
	@CsvSource(delimiter = '|', nullValues = "null",
			textBlock = """
					logging.level                                     | logging.level               | ''
					logging.level.com.example                         | logging.level               | com.example
					logging.level.levels                              | logging.level               | levels
					logging.level..leading                            | logging.level               | .leading
					logging.level.tc.floci/floci@sha256:405c128b...   | logging.level               | tc.floci/floci@sha256:405c128b...
					logging.route.console.level                       | logging.route.console.level | ''
					logging.route.console.level.com.example           | logging.route.console.level | com.example
					logging.route.level.level.com.example             | logging.route.level.level   | com.example
					logging.change.logger                             | logging.change.logger       | ''
					logging.change.logger.com.example                 | logging.change.logger       | com.example
					logging.caller.logger                             | logging.caller.logger       | ''
					logging.caller.logger.com.example                 | logging.caller.logger       | com.example
					logging.levels                                    | null                        | null
					logging.levelExtra                                | null                        | null
					logging.route.console.levels                      | null                        | null
					logging.route.level.typo                          | null                        | null
					logging.route.console.publisher.level             | null                        | null
					logging.route..level                              | null                        | null
					logging.changes                                   | null                        | null
					logging.change.mode                               | null                        | null
					logging.change.com.example                        | null                        | null
					logging.caller.com.example                        | null                        | null
					logging.global.change                             | null                        | null
					""")
	void parse(String key, @Nullable String prefix, @Nullable String loggerName) {
		var k = Loggers.Key.parseOrNull(key);
		if (prefix == null) {
			assertNull(k);
			return;
		}
		assertEquals(new Loggers.Key(prefix, name(loggerName)), k);
	}

	@ParameterizedTest
	@MethodSource("io.jstach.rainbowgum.LoggerNameKeyTest#loggerNames")
	void loggerNamesRoundTrip(String loggerName) {
		String key = Loggers.Key.key(LogProperties.LEVEL_PREFIX, loggerName);
		var k = Loggers.Key.parseOrNull(key);
		assertEquals(new Loggers.Key(LogProperties.LEVEL_PREFIX, loggerName), k);
	}

	private static String name(@Nullable String name) {
		return name == null ? "" : name;
	}

}

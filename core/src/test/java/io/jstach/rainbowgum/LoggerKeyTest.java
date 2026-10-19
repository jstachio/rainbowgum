package io.jstach.rainbowgum;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.jspecify.annotations.Nullable;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.MethodSource;

import io.jstach.rainbowgum.LogProperties.LoggerKey;

class LoggerKeyTest {

	@ParameterizedTest
	@CsvSource(delimiter = '|', nullValues = "null",
			textBlock = """
					logging.level                                  | LEVEL       | logging.level                | ''
					logging.level.com.example                      | LEVEL       | logging.level                | com.example
					logging.level.levels                           | LEVEL       | logging.level                | levels
					logging.level..leading                         | LEVEL       | logging.level                | .leading
					logging.level.tc.floci/floci@sha256:405c128b... | LEVEL      | logging.level                | tc.floci/floci@sha256:405c128b...
					logging.route.console.level                    | ROUTE_LEVEL | logging.route.console.level  | ''
					logging.route.console.level.com.example        | ROUTE_LEVEL | logging.route.console.level  | com.example
					logging.route.level.level.com.example          | ROUTE_LEVEL | logging.route.level.level    | com.example
					logging.change.logger                          | CHANGE      | logging.change.logger        | ''
					logging.change.logger.com.example              | CHANGE      | logging.change.logger        | com.example
					logging.caller.logger                          | CALLER      | logging.caller.logger        | ''
					logging.caller.logger.com.example              | CALLER      | logging.caller.logger        | com.example
					logging.levels                                 | null        | null                         | null
					logging.levelExtra                             | null        | null                         | null
					logging.route.console.levels                   | null        | null                         | null
					logging.route.level.typo                       | null        | null                         | null
					logging.route.console.publisher.level          | null        | null                         | null
					logging.route..level                           | null        | null                         | null
					logging.changes                                | null        | null                         | null
					logging.change.mode                            | null        | null                         | null
					logging.change.com.example                     | null        | null                         | null
					logging.caller.com.example                     | null        | null                         | null
					logging.global.change                          | null        | null                         | null
					""")
	void parse(String key, LoggerKey.@Nullable Namespace namespace, @Nullable String prefix,
			@Nullable String loggerName) {
		var k = LoggerKey.parseOrNull(key);
		if (namespace == null) {
			assertNull(k);
			return;
		}
		assertEquals(new DefaultLoggerKey(namespace, prefix(prefix), name(loggerName), key), k);
	}

	@ParameterizedTest
	@MethodSource("io.jstach.rainbowgum.LoggerNameKeyTest#loggerNames")
	void loggerNamesRoundTrip(String loggerName) {
		String key = DefaultLoggerKey.key(LogProperties.LEVEL_PREFIX, loggerName);
		var k = LoggerKey.parseOrNull(key);
		assertEquals(new DefaultLoggerKey(LoggerKey.Namespace.LEVEL, LogProperties.LEVEL_PREFIX, loggerName, key), k);
	}

	private static String prefix(@Nullable String prefix) {
		if (prefix == null) {
			throw new IllegalArgumentException();
		}
		return prefix;
	}

	private static String name(@Nullable String name) {
		return name == null ? "" : name;
	}

}

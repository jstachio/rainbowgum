package io.jstach.rainbowgum;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.jspecify.annotations.Nullable;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/*
 * A key that ends with a logger name is suggested by its prefix, keeping the logger name.
 */
class LoggerKeySuggestionTest {

	@ParameterizedTest
	@CsvSource({ "logging.levle, logging.level", //
			"logging.levels.com.example, logging.level.com.example", //
			"logging.Level.com.example, logging.level.com.example", //
			"logging.lvl.com.example, logging.level.com.example", //
			"logging.change.loger.com.example, logging.change.logger.com.example", //
			"logging.callr.logger.com.example, logging.caller.logger.com.example", //
			"logging.route.default.levle.com.example, logging.route.default.level.com.example", //
			"logging.route.web.levels, logging.route.web.level", //
			"logging.level.com.example, ", //
			"logging.file.name, ", //
			"logging.change.mode, ", //
			"logging.routes, ", //
			"logging.route.default.appendrs, ", //
			"logging.lever-arm.com.example, " })
	void loggerKeySuggestion(String key, @Nullable String expected) {
		assertEquals(expected, UnusedKeyCheck.loggerKeySuggestionOrNull(key));
	}

}

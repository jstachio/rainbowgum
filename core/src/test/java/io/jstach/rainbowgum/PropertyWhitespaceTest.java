package io.jstach.rainbowgum;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.System.Logger.Level;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import io.jstach.rainbowgum.LogConfig.Globals.AnsiMode;
import io.jstach.rainbowgum.LogConfig.Globals.AppenderLock;

/*
 * A properties file keeps trailing whitespace, which an editor does not show. Typed
 * values (enums, booleans, ints, levels) ignore it; plain strings keep it.
 */
class PropertyWhitespaceTest {

	private static LogConfig config(String properties) {
		return LogConfig.builder().properties(LogProperties.builder().fromProperties(properties).build()).build();
	}

	enum TypedCase {

		ENUM_VIA_PARSE("logging.global.ansi.mode=on \t\n") {
			@Override
			void check(LogConfig config) {
				assertEquals(AnsiMode.ON, config.globals().ansiMode());
			}
		},
		ENUM("logging.global.appender.lock=synchronized  \n") {
			@Override
			void check(LogConfig config) {
				assertEquals(AppenderLock.SYNCHRONIZED, config.globals().appenderLock());
			}
		},
		BOOLEAN("logging.global.optimize=true \n") {
			@Override
			void check(LogConfig config) {
				assertTrue(config.globals().optimize());
			}
		},
		INT("logging.alerts.capacity=7 \n") {
			@Override
			void check(LogConfig config) {
				assertEquals(7, config.alerts().stats().capacity());
			}
		},
		LEVEL("logging.level.com.example=warn \n") {
			@Override
			void check(LogConfig config) {
				assertEquals(Level.WARNING, config.levelResolver().resolveLevel("com.example"));
			}
		};

		final String properties;

		TypedCase(String properties) {
			this.properties = properties;
		}

		abstract void check(LogConfig config);

	}

	@ParameterizedTest
	@EnumSource(TypedCase.class)
	void typedValuesIgnoreTrailingWhitespace(TypedCase test) {
		test.check(config(test.properties));
	}

	@Test
	void badValueIsReportedWithoutTheWhitespace() {
		var e = assertThrows(LogProperty.ValidationException.class, () -> config("logging.global.ansi.mode=loud \n"));
		String expected = """
				Validation failed for io.jstach.rainbowgum.LogConfig:
				Error for property. key: 'logging.global.ansi.mode' from PROPERTIES_STRING[logging.global.ansi.mode], \
				'loud' is not a valid value for io.jstach.rainbowgum.LogConfig.Globals.AnsiMode. \
				Valid values: 'auto', 'on', 'off', 'true', 'false'""";
		assertEquals(expected, e.getMessage());
	}

	@Test
	void stringValuesKeepWhitespace() {
		var properties = LogProperties.builder().fromProperties("app.suffix=x \n").build();
		assertEquals("x ", properties.valueOrNull("app.suffix"));
	}

}

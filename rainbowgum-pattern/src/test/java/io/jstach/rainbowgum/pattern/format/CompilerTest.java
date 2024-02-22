package io.jstach.rainbowgum.pattern.format;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.lang.System.Logger.Level;
import java.time.Instant;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import io.jstach.rainbowgum.LogEvent;
import io.jstach.rainbowgum.pattern.format.FormatterFactory.FormatterConfig;

class CompilerTest {

	@ParameterizedTest
	@EnumSource(value = PatternTest.class)
	void test(PatternTest test) {
		Compiler c = new Compiler(PatternRegistry.of(), FormatterConfig.empty());
		StringBuilder sb = new StringBuilder();
		c.compile(test.input).format(sb, test.event());
		String actual = sb.toString();
		String expected = test.expected;
		test.output(actual);
		assertEquals(expected, actual);
		test.assertOutput(actual);
	}

	public static final boolean OUTPUT = false;

	enum PatternTest {

		DATE("%d", "1969-12-31 19:00:00,000"), LOGGER("%logger", "io.jstach.logger"),
		LOGGER_LEFT_PAD("%20logger", "    io.jstach.logger"), LOGGER_RIGHT_PAD("%-20logger", "io.jstach.logger    "),
		LOGGER_TRUNCATE("%.30logger", "logger.stu.123456789.123456789") {
			@Override
			protected String logger() {
				return "io.jstach.logger.stu.123456789.123456789";
			}
		},
		LOGGER_PAD_OR_TRUNCATE__PAD("%20.30logger", "          0123456789") {
			@Override
			protected String logger() {
				return "0123456789";
			}

			@Override
			protected void assertOutput(String output) {
				assertEquals(20, output.length());
			}
		},
		LOGGER_PAD_OR_TRUNCATE__TRUNC("%20.30logger", "012345678901234567890123456789") {
			@Override
			protected String logger() {
				return "0123456789" + "0123456789" + "0123456789" + "0123456789";
			}

			@Override
			protected void assertOutput(String output) {
				assertEquals(30, output.length());
			}
		},
		LOGGER_ABBREVIATE_ZERO("%logger{0}", "MyLogger") {
			@Override
			protected String logger() {
				return "io.jstach.logger.MyLogger";
			}
		},
		LOGGER_ABBREVIATE_10("%logger{10}", "i.j.l.MyLogger") {
			@Override
			protected String logger() {
				return "io.jstach.logger.MyLogger";
			}
		},
		COLOR_INFO("[%thread] %highlight(%-5level) %cyan(%logger{15}) - %msg%n",
				"[main] [34mINFO [0;39m [36mc.l.TriviaMain[0;39m - hello\n") {
			protected void output(String output) {
				if (OUTPUT)
					System.out.print(output);
			}

			@Override
			protected String logger() {
				return "com.logback.TriviaMain";
			}
		},
		COLOR_ERROR("[%thread] %highlight(%-5level) %cyan(%logger{15}) - %msg%n",
				"[main] [1;31mERROR[0;39m [36mc.l.TriviaMain[0;39m - hello\n") {
			protected void output(String output) {
				if (OUTPUT)
					System.out.print(output);
			}

			@Override
			protected String logger() {
				return "com.logback.TriviaMain";
			}

			@Override
			protected Level level() {
				return Level.ERROR;
			}
		},;

		final String input;

		final String expected;

		PatternTest(String input, String expected) {
			this.input = input;
			this.expected = expected;
		}

		protected String logger() {
			return "io.jstach.logger";
		}

		protected String message() {
			return "hello";
		}

		protected void output(String output) {
		}

		protected void assertOutput(String output) {
		}

		protected System.Logger.Level level() {
			return System.Logger.Level.INFO;
		}

		LogEvent event() {
			return LogEvent.of(level(), logger(), message(), null).freeze(Instant.EPOCH);
		}

	}

}

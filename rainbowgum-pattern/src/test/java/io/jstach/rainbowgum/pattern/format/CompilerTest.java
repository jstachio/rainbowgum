package io.jstach.rainbowgum.pattern.format;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.time.Instant;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import io.jstach.rainbowgum.LogEvent;

class CompilerTest {

	@ParameterizedTest
	@EnumSource(value = PatternTest.class)
	void test(PatternTest test) {
		Compiler c = new Compiler();
		StringBuilder sb = new StringBuilder();
		c.compile(test.input).format(sb, test.event());
		String actual = sb.toString();
		String expected = test.expected;
		test.output(actual);
		assertEquals(expected, actual);
		test.assertOutput(actual);
	}

	enum PatternTest {

		DATE("%d", "1969-12-31 19:00:00,000"),
		LOGGER("%logger", "io.jstach.logger"),
		LOGGER_LEFT_PAD("%20logger", "    io.jstach.logger"),
		LOGGER_RIGHT_PAD("%-20logger", "io.jstach.logger    "),
		LOGGER_TRUNCATE("%.30logger", "logger.stu.123456789.123456789"){
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
			protected void assertOutput(
					String output) {
				assertEquals(20, output.length());
			}
		},
		LOGGER_PAD_OR_TRUNCATE__TRUNC("%20.30logger", "012345678901234567890123456789") {
			@Override
			protected String logger() {
				return "0123456789" + "0123456789" + "0123456789" + "0123456789";
			}
			
			@Override
			protected void assertOutput(
					String output) {
				assertEquals(30, output.length());
			}
		},
		COLOR("[%thread] %highlight(%-5level) %cyan(%logger{15}) - %msg%n", "[main] [34mINFO [0;39m [36mio.jstach.logger[0;39m - hello\n") {
			protected void output(String output) {
				//System.out.println(output);
			}
		}
		;

		final String input;

		final String expected;

		PatternTest(String input, String expected) {
			this.input = input;
			this.expected = expected;
		}
		
		protected String logger() {
			return "io.jstach.logger";
		}
		
		protected void output(String output) {
		}
		
		protected void assertOutput(String output) {
			
		}

		LogEvent event() {
			return LogEvent.of(System.Logger.Level.INFO, logger(), "hello", null).freeze(Instant.EPOCH);
		}

	}

}

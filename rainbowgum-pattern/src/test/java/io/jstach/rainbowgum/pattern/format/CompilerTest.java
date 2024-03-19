package io.jstach.rainbowgum.pattern.format;

import static org.junit.jupiter.api.Assertions.*;

import java.lang.System.Logger.Level;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import io.jstach.rainbowgum.KeyValues;
import io.jstach.rainbowgum.LogEvent;
import io.jstach.rainbowgum.LogEvent.Caller;
import io.jstach.rainbowgum.LogFormatter;
import io.jstach.rainbowgum.LogFormatter.NameFormatter;
import io.jstach.rainbowgum.LogFormatter.TimestampFormatter;
import io.jstach.rainbowgum.pattern.format.PatternRegistry.KeywordKey;

class CompilerTest {

	@ParameterizedTest
	@EnumSource(value = PatternTest.class)
	void test(PatternTest test) {
		var c = PatternCompiler.builder()
			.patternRegistry(PatternRegistry.of())
			.patternConfig(test.patternConfig())
			.build();
		for (String input : test.inputs) {
			StringBuilder sb = new StringBuilder();
			var formatter = c.compile(input);
			formatter.format(sb, test.event());
			String actual = sb.toString();
			String expected = test.expected;
			test.output(actual);
			assertEquals(expected, test.filter(actual));
			test.assertOutput(actual);
			test.assertFormatter(formatter);
		}
	}

	@Test
	void confirmTestHasAllBuiltinKeywordPatterns() {
		for (var k : KeywordKey.values()) {
			try {
				var test = PatternTest.valueOf(k.name());
				List<String> patterns = k.aliases().stream().map(p -> "%" + p).toList();
				assertEquals(test.inputs, patterns);
			}
			catch (IllegalArgumentException e) {
				fail("Missing keyword test: " + k + " patterns: " + k.aliases());
			}
		}
	}

	@Test
	void testMissingKeyword() throws Exception {
		PatternConfig config = PatternConfig.builder().build();
		var c = PatternCompiler.builder().patternConfig(config).build();
		var e = assertThrows(IllegalArgumentException.class, () -> {
			c.compile("%missing");
		});
		String message = e.getMessage();
		assertEquals("Pattern is invalid: %missing", message);
	}

	public static final boolean OUTPUT = true;

	enum PatternTest {

		BARE("%BARE", ""), DATE(List.of("%d", "%date"), "1970-01-01 00:00:00,000") {
			@Override
			protected void assertFormatter(LogFormatter formatter) {
				assertInstanceOf(TimestampFormatter.class, formatter);
			}
		},
		MICROS(List.of("%ms", "%micros"), "000") {
			@Override
			protected void assertFormatter(LogFormatter formatter) {
				assertInstanceOf(TimestampFormatter.class, formatter);
			}
		},
		FILE(List.of("%f", "%file"), "CompilerTest.java") {
			LogEvent event() {
				Caller caller = Caller.ofDepthOrNull(1);
				return LogEvent.withCaller(super.event(), caller);
			}
		},
		LINE(List.of("%L", "%line"), "OK") {
			@Override
			protected void assertFormatter(LogFormatter formatter) {
				assertEquals(CallerInfoFormatter.LINE, formatter);
			}

			@Override
			protected String filter(String output) {
				int i = Integer.parseInt(output);
				assertTrue(i > 0);
				return "OK";
			}

			LogEvent event() {
				Caller caller = Caller.ofDepthOrNull(1);
				return LogEvent.withCaller(super.event(), caller);
			}
		},
		CLASS(List.of("%C", "%class"), CompilerTest.class.getName()) {
			LogEvent event() {
				Caller caller = Caller.ofDepthOrNull(1);
				return LogEvent.withCaller(super.event(), caller);
			}
		},
		METHOD(List.of("%M", "%method"), "test") {
			LogEvent event() {
				Caller caller = Caller.ofDepthOrNull(1);
				return LogEvent.withCaller(super.event(), caller);
			}
		},
		THREAD(List.of("%t", "%thread"), "main") {
		},
		LEVEL(List.of("%level", "%le", "%p"), "INFO") {
		},
		LOGGER(List.of("%lo", "%logger", "%c"), "io.jstach.logger") {
			@Override
			protected void assertFormatter(LogFormatter formatter) {
				assertInstanceOf(NameFormatter.class, formatter);
			}
		},
		MESSAGE(List.of("%m", "%msg", "%message"), "hello") {
		},
		MDC(List.of("%X", "%mdc"), "k1=v1") {
		},
		THROWABLE(List.of("%ex", "%exception", "%throwable"), "java.lang.RuntimeException: test_throwable") {
			LogEvent event() {
				Throwable throwable = new RuntimeException("test_throwable");
				return LogEvent.of(level(), logger(), message(), keyValues(), throwable).freeze(Instant.EPOCH);
			}

			@Override
			protected String filter(String output) {
				return Stream.of(output.split("\n")).limit(1).findFirst().orElse("FAIL");
			}
		},
		LINESEP("%n", "\n") {
		},
		LOGGER_LEFT_PAD("%20logger", "    io.jstach.logger") {
			@Override
			protected void assertFormatter(LogFormatter formatter) {
				assertInstanceOf(PadFormatter.class, formatter);
				if (formatter instanceof PadFormatter pf) {
					assertInstanceOf(NameFormatter.class, pf.formatter());
					assertEquals(20, pf.padding().min());
					assertTrue(pf.padding().leftPad());
					assertTrue(pf.padding().leftTruncate());
				}
			}
		},
		LOGGER_RIGHT_PAD("%-20logger", "io.jstach.logger    ") {
			@Override
			protected void assertFormatter(LogFormatter formatter) {
				System.out.println(formatter);
				assertInstanceOf(PadFormatter.class, formatter);
				if (formatter instanceof PadFormatter pf) {
					assertInstanceOf(NameFormatter.class, pf.formatter());
					assertEquals(20, pf.padding().min());
					assertFalse(pf.padding().leftPad());
					assertTrue(pf.padding().leftTruncate());
				}
			}
		},
		LOGGER_LEFT_PAD_MANY("%70logger", "                                                      io.jstach.logger") {
		},
		LOGGER_RIGHT_PAD_MANY("%-70logger", "io.jstach.logger                                                      ") {
		},
		LOGGER_LEFT_PAD_NOT_NEEDED("%20logger", "0123456789012345678901234567890123456789") {
			@Override
			protected String logger() {
				return "0123456789" + "0123456789" + "0123456789" + "0123456789";

			}

		},
		LOGGER_RIGHT_PAD_NOT_NEEDED("%-20logger", "0123456789012345678901234567890123456789") {
			@Override
			protected String logger() {
				return "0123456789" + "0123456789" + "0123456789" + "0123456789";

			}

		},
		LOGGER_TRUNCATE("%.30logger", "logger.stu.123456789.123456789") {
			@Override
			protected String logger() {
				return "io.jstach.logger.stu.123456789.123456789";
			}
		},
		LOGGER_PAD_OR_TRUNCATE__LPAD("%20.30logger", "          0123456789") {
			@Override
			protected String logger() {
				return "0123456789";
			}

			@Override
			protected void assertOutput(String output) {
				assertEquals(20, output.length());
			}
		},
		LOGGER_PAD_OR_TRUNCATE__RTRUNC("%20.30logger", "012345678901234567890123456789") {
			@Override
			protected String logger() {
				return "0123456789" + "0123456789" + "0123456789" + "0123456789";
			}

			@Override
			protected void assertOutput(String output) {
				assertEquals(30, output.length());
			}
		},
		LOGGER_PAD_OR_TRUNCATE__RPAD("%-20.30logger", "0123456789          ") {
			@Override
			protected String logger() {
				return "0123456789";
			}

			@Override
			protected void assertOutput(String output) {
				assertEquals(20, output.length());
			}
		},
		LOGGER_PAD_OR_TRUNCATE__LTRUNC("%20.30logger", "_12345678901234567890123456789") {
			@Override
			protected String logger() {
				return "abcdefghij" + "_123456789" + "0123456789" + "0123456789";
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
		FULL_INFO("[%thread] %-5level %logger{15} - %msg%n", "[main] INFO  c.l.TriviaMain - hello\n") {
			protected void output(String output) {
				if (OUTPUT)
					System.out.print(output);
			}

			@Override
			protected String logger() {
				return "com.logback.TriviaMain";
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
		},
		COLOR_INFO_ANSI_DISABLED("[%thread] %highlight(%-5level) %cyan(%logger{15}) - %msg%n",
				"[main] INFO  c.l.TriviaMain - hello\n") {
			protected void output(String output) {
				if (OUTPUT)
					System.out.print(output);
			}

			@Override
			protected String logger() {
				return "com.logback.TriviaMain";
			}

			@Override
			protected PatternConfig patternConfig() {
				return PatternConfig.ofUniversal();
			}
		},
		COLOR_ERROR_ANSI_DISABLED("[%thread] %highlight(%-5level) %cyan(%logger{15}) - %msg%n",
				"[main] ERROR c.l.TriviaMain - hello\n") {
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

			@Override
			protected PatternConfig patternConfig() {
				return PatternConfig.ofUniversal();
			}
		},;

		final List<String> inputs;

		final String expected;

		PatternTest(String input, String expected) {
			this(List.of(input), expected);
		}

		PatternTest(List<String> inputs, String expected) {
			this.inputs = inputs;
			this.expected = expected;
		}

		protected String logger() {
			return "io.jstach.logger";
		}

		protected String message() {
			return "hello";
		}

		protected String filter(String output) {
			return output;
		}

		protected void output(String output) {
		}

		protected void assertOutput(String output) {
		}

		protected void assertFormatter(LogFormatter formatter) {
		}

		protected System.Logger.Level level() {
			return System.Logger.Level.INFO;
		}

		protected PatternConfig patternConfig() {
			return PatternConfig.copy(PatternConfig.builder(), PatternConfig.ofUniversal()).ansiDisabled(false).build();
		}

		KeyValues keyValues() {
			return KeyValues.of(Map.of("k1", "v1"));
		}

		LogEvent event() {
			return LogEvent.of(level(), logger(), message(), keyValues(), null).freeze(Instant.EPOCH);
		}

	}

}

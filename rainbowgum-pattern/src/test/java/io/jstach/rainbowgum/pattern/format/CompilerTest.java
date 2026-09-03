package io.jstach.rainbowgum.pattern.format;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import java.lang.System.Logger.Level;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import io.jstach.rainbowgum.KeyValues;
import io.jstach.rainbowgum.KeyValues.MutableKeyValues;
import io.jstach.rainbowgum.LogEvent;
import io.jstach.rainbowgum.LogEvent.Caller;
import io.jstach.rainbowgum.LogFormatter;
import io.jstach.rainbowgum.LogFormatter.TimestampFormatter;
import io.jstach.rainbowgum.pattern.format.PatternRegistry.KeywordKey;

class CompilerTest {

	@ParameterizedTest
	@EnumSource(value = PatternTest.class)
	void test(PatternTest test) {
		var c = (Compiler) PatternCompiler.builder()
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
			/*
			 * Patterns that don't otherwise mention an exception keyword get a default
			 * exception formatter appended automatically (see Compiler.compile), which
			 * would otherwise mask what a single keyword like %L or %20logger actually
			 * compiles to. assertFormatter checks are about that keyword-to-formatter
			 * mapping, so they get the un-appended result instead.
			 */
			test.assertFormatter(c.compile(input, false));
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

	@Test
	void testLocalSequenceNumberIncrementsPerEvent() {
		var c = PatternCompiler.builder().patternConfig(PatternConfig.ofUniversal()).build();
		var formatter = c.compile("%lsn");
		var event = TestLogEventFactory.of("io.jstach.logger")
			.event(Level.INFO, "hello", MutableKeyValues.of().freeze(), (Throwable) null)
			.freeze(Instant.EPOCH);
		StringBuilder sb = new StringBuilder();
		formatter.format(sb, event);
		formatter.format(sb, event);
		formatter.format(sb, event);
		assertEquals("012", sb.toString());
	}

	public static final boolean OUTPUT = true;

	enum PatternTest {

		BARE("%BARE", ""), //
		BARE_WITH_CHILD("%BARE(hello)", "hello"), //
		DATE(List.of("%d", "%date"), "1970-01-01 00:00:00,000") {
			@Override
			protected void assertFormatter(LogFormatter formatter) {
				assertInstanceOf(TimestampFormatter.class, formatter);
			}
		},
		DATE_PARAMETER("%date{yyyy-MM-dd HH:mm:ss}", "1970-01-01 00:00:00") {
			@Override
			protected void assertFormatter(LogFormatter formatter) {
				assertInstanceOf(TimestampFormatter.class, formatter);
			}
		},
		DATE_ISO8601_LITERAL("%date{ISO8601}", "1970-01-01 00:00:00,000") {
		},
		/*
		 * Third option is a locale - "MMM" renders the month abbreviation, which differs
		 * by locale (unlike the numeric-only patterns the other DATE tests use), so this
		 * is the only way to prove the locale option is actually wired up rather than
		 * silently ignored.
		 */
		DATE_WITH_LOCALE("%date{MMM, UTC, fr-FR}", "janv.") {
		},
		MICROS(List.of("%ms", "%micros"), "000") {
			@Override
			protected void assertFormatter(LogFormatter formatter) {
				assertInstanceOf(TimestampFormatter.class, formatter);
			}
		},
		RELATIVE(List.of("%r", "%relative"), "0") {
		},
		RELATIVE_ELAPSED(List.of("%r"), "5000") {
			@Override
			protected PatternConfig patternConfig() {
				return PatternConfig.copy(PatternConfig.builder(), PatternConfig.ofUniversal())
					.startTime(Instant.EPOCH.minusMillis(5000))
					.build();
			}
		},
		LOCAL_SEQUENCE_NUMBER(List.of("%lsn"), "0") {
		},
		LOCAL_SEQUENCE_NUMBER_START(List.of("%lsn"), "5") {
			@Override
			protected PatternConfig patternConfig() {
				return PatternConfig.copy(PatternConfig.builder(), PatternConfig.ofUniversal())
					.sequenceNumberStart(5L)
					.build();
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
		/*
		 * Uses the default event(), which has no caller info at all - confirms
		 * CallerInfoFormatter is a noop rather than throwing when info is absent.
		 */
		LINE_WITHOUT_CALLER_INFO("%L", "") {
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
		PROPERTY(List.of("%property"), "Property_HAS_NO_KEY") {
		},
		PROPERTY_KEY(List.of("%property{mykey}"), "hello") {
			@Override
			protected PatternConfig patternConfig() {
				Map<String, String> props = Map.of("mykey", "hello");
				return PatternConfig.copy(PatternConfig.builder(), PatternConfig.ofUniversal())
					.propertyFunction(props::get)
					.build();
			}
		},
		// default patternConfig()'s propertyFunction never has a value for any key.
		PROPERTY_MISSING("%property{missingKey}", "") {
		},
		/*
		 * Padding a keyword whose formatter turns out to be a plain, event-independent
		 * StaticFormatter (a fixed property value here) takes a different, precomputed
		 * path in PadFormatter.of than padding something dynamic like a logger name.
		 */
		PROPERTY_PADDED_IS_PRECOMPUTED_STATIC("%10property{mykey}", "     hello") {
			@Override
			protected PatternConfig patternConfig() {
				Map<String, String> props = Map.of("mykey", "hello");
				return PatternConfig.copy(PatternConfig.builder(), PatternConfig.ofUniversal())
					.propertyFunction(props::get)
					.build();
			}

			@Override
			protected void assertFormatter(LogFormatter formatter) {
				assertInstanceOf(LogFormatter.StaticFormatter.class, formatter);
			}
		},
		// Spring Boot's %esb - bare, no key at all.
		ESB_NO_KEY("%esb", "") {
		},
		// key given but patternConfig()'s propertyFunction has no value for it.
		ESB_MISSING_VALUE("%esb{missingKey}", "") {
		},
		ESB_EMPTY_VALUE("%esb{emptyKey}", "") {
			@Override
			protected PatternConfig patternConfig() {
				Map<String, String> props = Map.of("emptyKey", "");
				return PatternConfig.copy(PatternConfig.builder(), PatternConfig.ofUniversal())
					.propertyFunction(props::get)
					.build();
			}
		},
		ESB_WITH_VALUE("%esb{mykey}", "[hello]") {
			@Override
			protected PatternConfig patternConfig() {
				Map<String, String> props = Map.of("mykey", "hello");
				return PatternConfig.copy(PatternConfig.builder(), PatternConfig.ofUniversal())
					.propertyFunction(props::get)
					.build();
			}
		},
		THREAD(List.of("%t", "%thread"), "main") {
		},
		LEVEL(List.of("%level", "%le", "%p"), "INFO") {
		},
		LOGGER(List.of("%lo", "%logger", "%c"), "io.jstach.logger") {
		},
		MESSAGE(List.of("%m", "%msg", "%message"), "hello") {
		},
		MDC(List.of("%X", "%mdc"), "k1=v1, k2=v2") {
		},
		MDC_KEY(List.of("%X{k2}", "%mdc{k2}"), "v2") {
		},
		// Logback's %X{key:-fallback} default-value syntax, for a key that is absent.
		MDC_FALLBACK("%X{missing:-none}", "none") {
		},
		ENCODED_MDC(List.of("%encodedMdc"), "k1=v1&k2=v2") {
		},
		ENCODED_MDC_KEY(List.of("%encodedMdc{k2}"), "k2=v2") {
		},
		THROWABLE(List.of("%ex", "%exception", "%throwable"), "java.lang.RuntimeException: test_throwable") {
			LogEvent event() {
				Throwable throwable = new RuntimeException("test_throwable");
				return TestLogEventFactory.of(logger())
					.event(level(), message(), keyValues(), throwable)
					.freeze(Instant.EPOCH);
			}

			@Override
			protected String filter(String output) {
				return Stream.of(output.split("\n")).limit(1).findFirst().orElse("FAIL");
			}
		},
		THROWABLE_MAX_LINES(List.of("%ex{2}"),
				"java.lang.RuntimeException: boom\n" + "\tat com.example.App.a(App.java:1)\n"
						+ "\tat com.example.App.b(App.java:2)\n" + "\t... 2 frames truncated\n") {
			LogEvent event() {
				Throwable throwable = throwableWithFrames("boom", "a", "b", "c", "d");
				return TestLogEventFactory.of(logger())
					.event(level(), message(), keyValues(), throwable)
					.freeze(Instant.EPOCH);
			}
		},
		THROWABLE_SHORT(List.of("%ex{short}"), "java.lang.RuntimeException: boom\n\t... 2 frames truncated\n") {
			LogEvent event() {
				Throwable throwable = throwableWithFrames("boom", "a", "b");
				return TestLogEventFactory.of(logger())
					.event(level(), message(), keyValues(), throwable)
					.freeze(Instant.EPOCH);
			}
		},
		THROWABLE_FULL(List.of("%ex{full}"), "java.lang.RuntimeException: boom\n"
				+ "\tat com.example.App.a(App.java:1)\n" + "\tat com.example.App.b(App.java:2)\n") {
			LogEvent event() {
				Throwable throwable = throwableWithFrames("boom", "a", "b");
				return TestLogEventFactory.of(logger())
					.event(level(), message(), keyValues(), throwable)
					.freeze(Instant.EPOCH);
			}
		},
		THROWABLE_EXCLUDE(List.of("%ex{full, noisyReflect}"), "java.lang.RuntimeException: boom\n"
				+ "\tat com.example.App.keepA(App.java:1)\n" + "\tat com.example.App.keepB(App.java:3)\n") {
			LogEvent event() {
				Throwable throwable = throwableWithFrames("boom", "keepA", "noisyReflect", "keepB");
				return TestLogEventFactory.of(logger())
					.event(level(), message(), keyValues(), throwable)
					.freeze(Instant.EPOCH);
			}
		},
		EXTENDED_THROWABLE(List.of("%xEx", "%xException", "%xThrowable"), "java.lang.RuntimeException: boom\n"
				+ "\tat com.example.App.a(App.java:1) [na:na]\n" + "\tat com.example.App.b(App.java:2) [na:na]\n") {
			LogEvent event() {
				Throwable throwable = throwableWithFrames("boom", "a", "b");
				return TestLogEventFactory.of(logger())
					.event(level(), message(), keyValues(), throwable)
					.freeze(Instant.EPOCH);
			}
		},
		EXTENDED_THROWABLE_MAX_LINES(List.of("%xEx{2}"),
				"java.lang.RuntimeException: boom\n" + "\tat com.example.App.a(App.java:1) [na:na]\n"
						+ "\tat com.example.App.b(App.java:2) [na:na]\n" + "\t... 2 frames truncated\n") {
			LogEvent event() {
				Throwable throwable = throwableWithFrames("boom", "a", "b", "c", "d");
				return TestLogEventFactory.of(logger())
					.event(level(), message(), keyValues(), throwable)
					.freeze(Instant.EPOCH);
			}
		},
		NO_EXCEPTION(List.of("%nopex", "%nopexception"), "") {
			LogEvent event() {
				Throwable throwable = new RuntimeException("should not appear");
				return TestLogEventFactory.of(logger())
					.event(level(), message(), keyValues(), throwable)
					.freeze(Instant.EPOCH);
			}

			@Override
			protected void assertFormatter(LogFormatter formatter) {
				assertTrue(LogFormatter.isNoopOrNull(formatter));
			}
		},
		NO_EXCEPTION_SUPPRESSES_AUTO_APPEND("%msg%nopex", "hello") {
			LogEvent event() {
				Throwable throwable = new RuntimeException("should not appear");
				return TestLogEventFactory.of(logger())
					.event(level(), message(), keyValues(), throwable)
					.freeze(Instant.EPOCH);
			}
		},
		LINESEP("%n", "\n") {
		},
		LOGGER_LEFT_PAD("%20logger", "    io.jstach.logger") {
			@Override
			protected void assertFormatter(LogFormatter formatter) {
				assertInstanceOf(PadFormatter.class, formatter);
				if (formatter instanceof PadFormatter pf) {
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
		/*
		 * This is doc/overview.html's own first pattern example, verbatim - it mentions
		 * no exception keyword at all, so this confirms the automatic exception formatter
		 * kicks in rather than silently dropping the throwable, which is exactly the gap
		 * that motivated adding it.
		 */
		FULL_INFO_SHOWS_EXCEPTION_WITHOUT_EXPLICIT_KEYWORD("[%thread] %-5level %logger{15} - %msg%n",
				"[main] INFO  c.l.TriviaMain - hello\n") {
			LogEvent event() {
				Throwable throwable = new RuntimeException("boom");
				return TestLogEventFactory.of(logger())
					.event(level(), message(), keyValues(), throwable)
					.freeze(Instant.EPOCH);
			}

			@Override
			protected String logger() {
				return "com.logback.TriviaMain";
			}

			@Override
			protected String filter(String output) {
				return output.lines().findFirst().orElse("FAIL") + "\n";
			}

			@Override
			protected void assertOutput(String output) {
				assertTrue(output.contains("java.lang.RuntimeException: boom"), output);
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
		},
		// Bare, no parens/child at all - just the ANSI wrap around nothing.
		HIGHLIGHT_NO_CHILD("%highlight", ANSIConstants.ESC_START + ANSIConstants.BLUE_FG + ANSIConstants.ESC_END
				+ ANSIConstants.SET_DEFAULT_COLOR) {
		},
		// ansiDisabled + no child at all: noop, same as CLR_ANSI_DISABLED_NO_CHILD.
		HIGHLIGHT_ANSI_DISABLED_NO_CHILD("%highlight", "") {
			@Override
			protected PatternConfig patternConfig() {
				return PatternConfig.ofUniversal();
			}
		},
		// DEBUG/TRACE/ALL/OFF all fall through HighlightFormatter's levelToANSI default.
		HIGHLIGHT_DEBUG_LEVEL("%highlight(%level)", ANSIConstants.ESC_START + ANSIConstants.DEFAULT_FG
				+ ANSIConstants.ESC_END + "DEBUG" + ANSIConstants.SET_DEFAULT_COLOR) {
			@Override
			protected Level level() {
				return Level.DEBUG;
			}
		},
		// Bare color keyword, no child at all - just the ANSI wrap around nothing.
		COLOR_BARE_NO_CHILD("%red", ANSIConstants.ESC_START + ANSIConstants.RED_FG + ANSIConstants.ESC_END
				+ ANSIConstants.SET_DEFAULT_COLOR) {
		},
		// Spring Boot's %clr with no color option: level-based coloring (ERROR/red).
		CLR_LEVEL_ERROR("%clr(%level)", ANSIConstants.ESC_START + ANSIConstants.RED_FG + ANSIConstants.ESC_END + "ERROR"
				+ ANSIConstants.SET_DEFAULT_COLOR) {
			@Override
			protected Level level() {
				return Level.ERROR;
			}
		},
		CLR_LEVEL_WARNING("%clr(%level)", ANSIConstants.ESC_START + ANSIConstants.YELLOW_FG + ANSIConstants.ESC_END
				+ "WARN" + ANSIConstants.SET_DEFAULT_COLOR) {
			@Override
			protected Level level() {
				return Level.WARNING;
			}
		},
		CLR_LEVEL_INFO("%clr(%level)", ANSIConstants.ESC_START + ANSIConstants.GREEN_FG + ANSIConstants.ESC_END + "INFO"
				+ ANSIConstants.SET_DEFAULT_COLOR) {
		},
		CLR_LEVEL_DEBUG("%clr(%level)", ANSIConstants.ESC_START + ANSIConstants.GREEN_FG + ANSIConstants.ESC_END
				+ "DEBUG" + ANSIConstants.SET_DEFAULT_COLOR) {
			@Override
			protected Level level() {
				return Level.DEBUG;
			}
		},
		CLR_LEVEL_TRACE("%clr(%level)", ANSIConstants.ESC_START + ANSIConstants.GREEN_FG + ANSIConstants.ESC_END
				+ "TRACE" + ANSIConstants.SET_DEFAULT_COLOR) {
			@Override
			protected Level level() {
				return Level.TRACE;
			}
		},
		/*
		 * ALL/OFF are not realistic event levels but exercise levelToANSI's default arm -
		 * the %level formatter itself renders Level.ALL's text as "TRACE", which is
		 * unrelated to (and doesn't affect) which color branch this is testing.
		 */
		CLR_LEVEL_DEFAULT_BRANCH("%clr(%level)", ANSIConstants.ESC_START + ANSIConstants.DEFAULT_FG
				+ ANSIConstants.ESC_END + "TRACE" + ANSIConstants.SET_DEFAULT_COLOR) {
			@Override
			protected Level level() {
				return Level.ALL;
			}
		},
		// Bare %clr, no parens/option - level-based color around nothing.
		CLR_NO_CHILD("%clr", ANSIConstants.ESC_START + ANSIConstants.GREEN_FG + ANSIConstants.ESC_END
				+ ANSIConstants.SET_DEFAULT_COLOR) {
		},
		// Explicit color option overrides the level-based color entirely.
		CLR_WITH_EXPLICIT_COLOR("%clr(%level){red}", ANSIConstants.ESC_START + ANSIConstants.RED_FG
				+ ANSIConstants.ESC_END + "INFO" + ANSIConstants.SET_DEFAULT_COLOR) {
		},
		// The color option works without parens/a child too.
		CLR_WITH_EXPLICIT_COLOR_NO_CHILD("%clr{red}", ANSIConstants.ESC_START + ANSIConstants.RED_FG
				+ ANSIConstants.ESC_END + ANSIConstants.SET_DEFAULT_COLOR) {
		},
		// ansiDisabled + a child: passes the child through untouched, no escape codes.
		CLR_ANSI_DISABLED_WITH_CHILD("%clr(%level)", "INFO") {
			@Override
			protected PatternConfig patternConfig() {
				return PatternConfig.ofUniversal();
			}
		},
		// ansiDisabled + no child at all: noop.
		CLR_ANSI_DISABLED_NO_CHILD("%clr", "") {
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
			return MutableKeyValues.of().add("k1", "v1").add("k2", "v2").freeze();
		}

		LogEvent event() {
			return TestLogEventFactory.of(logger())
				.event(level(), message(), keyValues(), (Throwable) null)
				.freeze(Instant.EPOCH);
		}

		static Throwable throwableWithFrames(String message, String... methodNames) {
			var throwable = new RuntimeException(message);
			var frames = new StackTraceElement[methodNames.length];
			for (int i = 0; i < methodNames.length; i++) {
				frames[i] = new StackTraceElement("com.example.App", methodNames[i], "App.java", i + 1);
			}
			throwable.setStackTrace(frames);
			return throwable;
		}

	}

}

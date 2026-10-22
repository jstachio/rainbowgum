package io.jstach.rainbowgum;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.lang.System.Logger.Level;
import java.time.Instant;
import java.util.Arrays;
import java.util.stream.Stream;

import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;

import io.jstach.rainbowgum.LogMessageFormatter.StandardMessageFormatter;

/**
 * The java.util.logging and System.Logger MessageFormat style by default logs what the
 * JDK does for the same call, and what Log4j2 does when configured.
 */
class MessageFormatStyleTest {

	@SuppressWarnings("StaticAssignmentOfThrowable")
	static final RuntimeException EXCEPTION = new RuntimeException("boom");

	static final class BadToString {

		@Override
		public String toString() {
			throw new RuntimeException("expected");
		}

	}

	enum Style {

		DEFAULT(""), //
		JDK("logging.messageFormat.style=jdk"), //
		JBOSS("logging.messageFormat.style=jboss"), //
		LOG4J2("logging.messageFormat.style=log4j2"), //
		LOGBACK("logging.messageFormat.style=logback"), //
		;

		final String properties;

		Style(String properties) {
			this.properties = properties;
		}

		LogMessageFormatter formatter() {
			return LogMessageFormatter.ofJUL(LogProperties.builder().fromProperties(properties).build());
		}

		boolean log4j2() {
			return this == LOG4J2 || this == LOGBACK;
		}

	}

	@SuppressWarnings("ImmutableEnumChecker")
	enum JULCase {

		APOSTROPHE_NO_INDEX("It's done", "Its done", "It's done", "x"), //
		APOSTROPHE_QUOTED("It''s {0}", "It's x", "It's x", "x"), //
		APOSTROPHE_UNQUOTED("It's {0}", "Its {0}", "Its {0}", "x"), //
		NO_ARGS("It's {0}", "It's {0}", "It's {0}"), //
		UNMATCHED_NO_INDEX("unmatched {", "unmatched { [MessageFormat failed: Unmatched braces in the pattern.]",
				"unmatched {", "x"), //
		UNMATCHED("unmatched {0", "unmatched {0 [MessageFormat failed: Unmatched braces in the pattern.]",
				"unmatched {0 [MessageFormat failed: Unmatched braces in the pattern.]", "x"), //
		BAD_INDEX("{a} and {0}", "{a} and {0} [MessageFormat failed: can't parse argument number: a]",
				"{a} and {0} [MessageFormat failed: can't parse argument number: a]", "x"), //
		BAD_TO_STRING("value {0}", "value {0} [MessageFormat failed: expected]",
				"value {0} [MessageFormat failed: expected]", new BadToString()), //
		SLF4J_DELIMITER("a={0} b={1}", "a=X b={}", "a=X b={}", "X", "{}"), //
		THREE_ARGS("{0} {1} {2}", "a b c", "a b c", "a", "b", "c"), //
		ONE_THROWABLE("failed {0}", "failed java.lang.RuntimeException: boom",
				"failed java.lang.RuntimeException: boom", EXCEPTION), //
		TRAILING_THROWABLE("user {0} failed {1}", "user bob failed java.lang.RuntimeException: boom",
				"user bob failed java.lang.RuntimeException: boom", "bob", EXCEPTION), //
		TRAILING_THROWABLE_THREE_ARGS("{0} {1} failed {2}", "a b failed java.lang.RuntimeException: boom",
				"a b failed java.lang.RuntimeException: boom", "a", "b", EXCEPTION), //
		;

		final String message;

		final String expectedLog4j2;

		final String expectedJDK;

		final @Nullable Object[] args;

		JULCase(String message, String expectedLog4j2, String expectedJDK, @Nullable Object... args) {
			this.message = message;
			this.expectedLog4j2 = expectedLog4j2;
			this.expectedJDK = expectedJDK;
			this.args = args;
		}

		String expected(Style style) {
			return style.log4j2() ? expectedLog4j2 : expectedJDK;
		}

		@Nullable Throwable expectedThrowable(Style style) {
			if (style.log4j2() && args.length > 0 && args[args.length - 1] instanceof Throwable t) {
				return t;
			}
			return null;
		}

	}

	static Stream<Arguments> julCases() {
		return Stream.of(Style.values()).flatMap(a -> Stream.of(JULCase.values()).map(c -> Arguments.of(a, c)));
	}

	@ParameterizedTest
	@MethodSource("julCases")
	void julArray(Style style, JULCase c) {
		var event = LogEvent.ofAll(Instant.EPOCH, "main", 1, Level.INFO, "test", c.message, KeyValues.of(), null,
				style.formatter(), c.args);
		assertJUL(style, c, event);
	}

	@ParameterizedTest
	@MethodSource("julCases")
	void julList(Style style, JULCase c) {
		var event = LogEvent.ofAll(Instant.EPOCH, "main", 1, Level.INFO, "test", c.message, KeyValues.of(), null,
				style.formatter(), Arrays.asList(c.args));
		assertJUL(style, c, event);
	}

	@ParameterizedTest
	@MethodSource("julCases")
	void julOneOrTwoArgs(Style style, JULCase c) {
		var formatter = style.formatter();
		var event = switch (c.args.length) {
			case 1 -> LogEvent.ofOneArg(Instant.EPOCH, "main", 1, Level.INFO, "test", c.message, KeyValues.of(),
					formatter, c.args[0]);
			case 2 -> LogEvent.ofTwoArgs(Instant.EPOCH, "main", 1, Level.INFO, "test", c.message, KeyValues.of(),
					formatter, c.args[0], c.args[1]);
			default -> null;
		};
		if (event != null) {
			assertJUL(style, c, event);
		}
	}

	private static void assertJUL(Style style, JULCase c, LogEvent event) {
		assertEquals(c.expected(style), formatted(event));
		assertEquals(String.valueOf(c.expectedThrowable(style)), String.valueOf(event.throwableOrNull()));
		var frozen = event.freeze();
		assertEquals(c.expected(style), formatted(frozen));
		assertEquals(String.valueOf(c.expectedThrowable(style)), String.valueOf(frozen.throwableOrNull()));
	}

	@ParameterizedTest
	@EnumSource(Style.class)
	void ofJULReturnsTheFormatterOfTheStyle(Style style) {
		var expected = style.log4j2() ? Log4j2MessageFormatter.INSTANCE : StandardMessageFormatter.JUL;
		assertSame(expected, style.formatter());
	}

	@ParameterizedTest
	@EnumSource(ArgCount.class)
	void julTrailingThrowableIsArgumentAndThrowableWithLog4j2Style(ArgCount count) {
		var formatter = LogMessageFormatter
			.ofJUL(LogProperties.builder().fromProperties("logging.messageFormat.style=log4j2").build());
		var event = switch (count) {
			case ONE -> LogEvent.ofOneArg(Instant.EPOCH, "main", 1, Level.INFO, "test", "failed {0}", KeyValues.of(),
					formatter, EXCEPTION);
			case TWO -> LogEvent.ofTwoArgs(Instant.EPOCH, "main", 1, Level.INFO, "test", "user {0} failed {1}",
					KeyValues.of(), formatter, "bob", EXCEPTION);
			case ARRAY -> LogEvent.ofAll(Instant.EPOCH, "main", 1, Level.INFO, "test", "{0} {1} failed {2}",
					KeyValues.of(), null, formatter, new @Nullable Object[] { "a", "b", EXCEPTION });
		};
		String expected = switch (count) {
			case ONE -> "failed java.lang.RuntimeException: boom";
			case TWO -> "user bob failed java.lang.RuntimeException: boom";
			case ARRAY -> "a b failed java.lang.RuntimeException: boom";
		};
		assertEquals(expected, formatted(event));
		assertEquals("java.lang.RuntimeException: boom", String.valueOf(event.throwableOrNull()));
		var frozen = event.freeze();
		assertEquals(expected, formatted(frozen));
		assertEquals("java.lang.RuntimeException: boom", String.valueOf(frozen.throwableOrNull()));
	}

	@Test
	void ofJULInvalidValueIsReported() {
		var properties = LogProperties.builder().fromProperties("""
				logging.messageFormat.style=tinylog
				""").build();
		var e = assertThrows(RuntimeException.class, () -> LogMessageFormatter.ofJUL(properties));
		assertEquals(
				"""
						Validation failed for io.jstach.rainbowgum.LogMessageFormatter:
						Error for property. key: 'logging.messageFormat.style' from PROPERTIES_STRING[logging.messageFormat.style], 'tinylog' is not a valid value for io.jstach.rainbowgum.MessageFormatStyle. Valid values: 'jdk', 'log4j2', 'default', 'jboss', 'logback'""",
				e.getMessage());
	}

	@ParameterizedTest
	@EnumSource(ArgCount.class)
	void slf4jTrailingThrowableIsTheThrowable(ArgCount count) {
		var event = switch (count) {
			case ONE -> LogEvent.ofOneArg(Instant.EPOCH, "main", 1, Level.INFO, "test", "failed", KeyValues.of(),
					StandardMessageFormatter.SLF4J, EXCEPTION);
			case TWO -> LogEvent.ofTwoArgs(Instant.EPOCH, "main", 1, Level.INFO, "test", "user {} failed",
					KeyValues.of(), StandardMessageFormatter.SLF4J, "bob", EXCEPTION);
			case ARRAY -> LogEvent.ofAll(Instant.EPOCH, "main", 1, Level.INFO, "test", "{} {} failed", KeyValues.of(),
					null, StandardMessageFormatter.SLF4J, new @Nullable Object[] { "a", "b", EXCEPTION });
		};
		String expected = switch (count) {
			case ONE -> "failed";
			case TWO -> "user bob failed";
			case ARRAY -> "a b failed";
		};
		assertEquals(expected, formatted(event));
		assertEquals("java.lang.RuntimeException: boom", String.valueOf(event.throwableOrNull()));
	}

	enum ArgCount {

		ONE, TWO, ARRAY

	}

	private static String formatted(LogEvent event) {
		StringBuilder sb = new StringBuilder();
		event.formattedMessage(sb);
		return sb.toString();
	}

}

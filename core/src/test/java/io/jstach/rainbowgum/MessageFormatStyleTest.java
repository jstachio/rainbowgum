package io.jstach.rainbowgum;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
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
 * The java.util.logging and System.Logger MessageFormat style by default logs what Log4j2
 * and Logback do for the same call, and what the JDK does when configured.
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

	enum Apply {

		DEFAULT(""), //
		ARGUMENTS("logging.messageFormat.apply=arguments"), //
		INDEX("logging.messageFormat.apply=index"), //
		;

		final String properties;

		Apply(String properties) {
			this.properties = properties;
		}

		LogMessageFormatter formatter() {
			return LogMessageFormatter.ofJUL(LogProperties.builder().fromProperties(properties).build());
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

		final String expectedArguments;

		final String expectedIndex;

		final @Nullable Object[] args;

		JULCase(String message, String expectedArguments, String expectedIndex, @Nullable Object... args) {
			this.message = message;
			this.expectedArguments = expectedArguments;
			this.expectedIndex = expectedIndex;
			this.args = args;
		}

		String expected(Apply apply) {
			return apply == Apply.INDEX ? expectedIndex : expectedArguments;
		}

	}

	static Stream<Arguments> julCases() {
		return Stream.of(Apply.values()).flatMap(a -> Stream.of(JULCase.values()).map(c -> Arguments.of(a, c)));
	}

	@ParameterizedTest
	@MethodSource("julCases")
	void julArray(Apply apply, JULCase c) {
		var event = LogEvent.ofAll(Instant.EPOCH, "main", 1, Level.INFO, "test", c.message, KeyValues.of(), null,
				apply.formatter(), c.args);
		assertJUL(apply, c, event);
	}

	@ParameterizedTest
	@MethodSource("julCases")
	void julList(Apply apply, JULCase c) {
		var event = LogEvent.ofAll(Instant.EPOCH, "main", 1, Level.INFO, "test", c.message, KeyValues.of(), null,
				apply.formatter(), Arrays.asList(c.args));
		assertJUL(apply, c, event);
	}

	@ParameterizedTest
	@MethodSource("julCases")
	void julOneOrTwoArgs(Apply apply, JULCase c) {
		var formatter = apply.formatter();
		var event = switch (c.args.length) {
			case 1 -> LogEvent.ofOneArg(Instant.EPOCH, "main", 1, Level.INFO, "test", c.message, KeyValues.of(),
					formatter, c.args[0]);
			case 2 -> LogEvent.ofTwoArgs(Instant.EPOCH, "main", 1, Level.INFO, "test", c.message, KeyValues.of(),
					formatter, c.args[0], c.args[1]);
			default -> null;
		};
		if (event != null) {
			assertJUL(apply, c, event);
		}
	}

	private static void assertJUL(Apply apply, JULCase c, LogEvent event) {
		assertEquals(c.expected(apply), formatted(event));
		assertNull(event.throwableOrNull());
		assertEquals(c.expected(apply), formatted(event.freeze()));
	}

	@ParameterizedTest
	@EnumSource(Apply.class)
	void ofJULDefaultsAreTheEnum(Apply apply) {
		var formatter = apply.formatter();
		if (apply == Apply.INDEX) {
			assertNotSame(StandardMessageFormatter.JUL, formatter);
		}
		else {
			assertSame(StandardMessageFormatter.JUL, formatter);
		}
		var both = LogMessageFormatter.ofJUL(LogProperties.builder()
			.fromProperties("logging.messageFormat.throwable=argument\n" + apply.properties)
			.build());
		assertEquals(formatter, both);
	}

	@ParameterizedTest
	@EnumSource(ArgCount.class)
	void julTrailingThrowableBothIsArgumentAndThrowable(ArgCount count) {
		var formatter = LogMessageFormatter
			.ofJUL(LogProperties.builder().fromProperties("logging.messageFormat.throwable=both").build());
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
	void ofJULInvalidValuesAreReported() {
		var properties = LogProperties.builder().fromProperties("""
				logging.messageFormat.apply=sometimes
				logging.messageFormat.throwable=never
				""").build();
		var e = assertThrows(RuntimeException.class, () -> LogMessageFormatter.ofJUL(properties));
		assertEquals(
				"""
						Validation failed for io.jstach.rainbowgum.LogMessageFormatter:
						Error for property. key: 'logging.messageFormat.apply' from PROPERTIES_STRING[logging.messageFormat.apply], 'sometimes' is not a valid value for io.jstach.rainbowgum.JULMessageFormatter.Apply. Valid values: 'arguments', 'index'
						Error for property. key: 'logging.messageFormat.throwable' from PROPERTIES_STRING[logging.messageFormat.throwable], 'never' is not a valid value for io.jstach.rainbowgum.JULMessageFormatter.ThrowableArgument. Valid values: 'argument', 'both'""",
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

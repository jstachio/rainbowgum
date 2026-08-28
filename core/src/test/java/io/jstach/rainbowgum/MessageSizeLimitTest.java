package io.jstach.rainbowgum;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import java.lang.System.Logger.Level;

import org.junit.jupiter.api.Test;

import io.jstach.rainbowgum.LogMessageFormatter.StandardMessageFormatter;

/*
 * Exploration branch A of the message-size-limiting design discussion (see todo.md):
 * bounding LogEvent's message formatting itself with a maxSize parameter so the append
 * work can stop early instead of materializing an unbounded message before truncating.
 */
class MessageSizeLimitTest {

	private static final KeyValues NO_KEY_VALUES = KeyValues.of();

	@Test
	void shortMessageUnderMaxSizeIsIdenticalBoundedOrNot() {
		var event = LogEvent.of(Level.INFO, "logger", "Hello {}!", NO_KEY_VALUES, StandardMessageFormatter.SLF4J,
				"world");

		StringBuilder unbounded = new StringBuilder();
		event.formattedMessage(unbounded);

		StringBuilder bounded = new StringBuilder();
		event.formattedMessage(bounded, 1000);

		assertEquals("Hello world!", unbounded.toString());
		assertEquals(unbounded.toString(), bounded.toString());
	}

	@Test
	void messageExceedingMaxSizeIsCappedToExactlyMaxSize() {
		var event = LogEvent.of(Level.INFO, "logger", "Hello {}!", NO_KEY_VALUES, StandardMessageFormatter.SLF4J,
				"world");

		StringBuilder sb = new StringBuilder();
		event.formattedMessage(sb, 5);

		assertEquals(5, sb.length());
		assertEquals("Hello", sb.toString());
	}

	@Test
	void multipleArgsStopsAsSoonAsCapIsCrossedTailArgNeverAppears() {
		Object[] args = new Object[] { "AAAA", "BBBB", "CCCCCCCCCCCCCCCCCCCCCCCCCCCC" };
		var event = LogEvent.ofArgs(Level.INFO, "logger", "{} {} {}", NO_KEY_VALUES, StandardMessageFormatter.SLF4J,
				args);

		StringBuilder sb = new StringBuilder();
		// Cap lands inside the second argument's contribution - the third argument's
		// content must never be touched/appended at all.
		event.formattedMessage(sb, 6);

		assertEquals(6, sb.length());
		assertFalse(sb.toString().contains("C"), "third arg must never have been appended: " + sb);
	}

	@Test
	void arrayArgLogEventCapAcrossManyArgsNeverReachesLastArg() {
		Object[] args = new Object[] { "one", "two", "three", "four", "VERY_LONG_TAIL_MARKER" };
		var event = LogEvent.ofArgs(Level.INFO, "logger", "{}-{}-{}-{}-{}", NO_KEY_VALUES,
				StandardMessageFormatter.SLF4J, args);

		StringBuilder sb = new StringBuilder();
		event.formattedMessage(sb, 10);

		assertEquals(10, sb.length());
		assertFalse(sb.toString().contains("VERY_LONG_TAIL_MARKER"));
	}

	@Test
	void defaultLogEventPlainMessageIsCappedCorrectly() {
		var event = LogEvent.of(Level.INFO, "logger", "0123456789ABCDEF", NO_KEY_VALUES, null);

		StringBuilder sb = new StringBuilder();
		event.formattedMessage(sb, 8);

		assertEquals("01234567", sb.toString());
	}

	@Test
	void nonPositiveMaxSizeMeansUnboundedAndMatchesNoArgOverload() {
		var event = LogEvent.of(Level.INFO, "logger", "Hello {}!", NO_KEY_VALUES, StandardMessageFormatter.SLF4J,
				"world");

		StringBuilder unbounded = new StringBuilder();
		event.formattedMessage(unbounded);

		StringBuilder zero = new StringBuilder();
		event.formattedMessage(zero, 0);

		StringBuilder negative = new StringBuilder();
		event.formattedMessage(negative, -1);

		assertEquals(unbounded.toString(), zero.toString());
		assertEquals(unbounded.toString(), negative.toString());
	}

	@Test
	void preExistingBuilderContentIsNeverTruncatedOnlyTheAppendedPortionIsBounded() {
		var event = LogEvent.of(Level.INFO, "logger", "Hello {}!", NO_KEY_VALUES, StandardMessageFormatter.SLF4J,
				"world");

		StringBuilder sb = new StringBuilder("PREFIX:");
		event.formattedMessage(sb, 5);

		assertTrue(sb.toString().startsWith("PREFIX:"), sb.toString());
		assertEquals("PREFIX:Hello", sb.toString());
	}

	@Test
	void twoArgLogEventStopsBeforeSecondArgWhenCapLandsInFirst() {
		var event = LogEvent.of(Level.INFO, "logger", "{} {}", NO_KEY_VALUES, StandardMessageFormatter.SLF4J, "first",
				"SECOND_MUST_NOT_APPEAR");

		StringBuilder sb = new StringBuilder();
		event.formattedMessage(sb, 3);

		assertEquals(3, sb.length());
		assertFalse(sb.toString().contains("SECOND_MUST_NOT_APPEAR"));
	}

	@Test
	void stackFrameLogEventDelegatesEarlyExitInsteadOfFallingBackToDefault() {
		// StackFrameLogEvent (added via LogEvent.withCaller) must delegate
		// formattedMessage(sb, maxSize) to the wrapped event's own override, not
		// silently fall back to the generic format-then-truncate default - otherwise
		// any event with caller info attached would lose the early-exit behavior.
		var inner = LogEvent.ofArgs(Level.INFO, "logger", "{} {} {}", NO_KEY_VALUES, StandardMessageFormatter.SLF4J,
				new Object[] { "A", "B", "TAIL_SHOULD_NOT_APPEAR" });
		var caller = LogEvent.Caller.ofDepthOrNull(0);
		if (caller == null) {
			fail("expected a real caller frame at depth 0");
			throw new IllegalStateException();
		}
		var withCaller = LogEvent.withCaller(inner, caller);

		StringBuilder sb = new StringBuilder();
		withCaller.formattedMessage(sb, 3);

		assertEquals(3, sb.length());
		assertFalse(sb.toString().contains("TAIL_SHOULD_NOT_APPEAR"));
	}

}

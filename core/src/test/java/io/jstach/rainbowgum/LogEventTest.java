package io.jstach.rainbowgum;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import java.lang.System.Logger.Level;
import java.net.URI;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import org.eclipse.jdt.annotation.Nullable;
import org.junit.jupiter.api.Test;

import io.jstach.rainbowgum.KeyValues.MutableKeyValues;
import io.jstach.rainbowgum.LogEvent.Caller;

class LogEventTest {

	@Test
	void testOfLevelLoggerNameFormattedMessageKeyValuesThrowable() {
		Level level = Level.INFO;
		String loggerName = "logger";
		String formattedMessage = "Hello!";
		KeyValues keyValues = KeyValues.of();
		Throwable throwable = null;
		var event = LogEvent.of(level, loggerName, formattedMessage, keyValues, throwable);
		assertNotNull(event);
		assertNull(event.throwableOrNull());
		assertTrue(event.keyValues().isEmpty());
		assertTrue(event.hasMessage());
		assertEquals(Level.INFO, event.level());
		assertInstanceOf(DefaultLogEvent.class, event);
		StringBuilder sb = new StringBuilder();
		event.formattedMessage(sb);
		assertEquals("Hello!", sb.toString());
		sb.setLength(0);
		event.formattedMessage(sb, "NOT EXPECTED");
		assertEquals("Hello!", sb.toString());
		assertFreeze(event);
	}

	@Test
	void testOfLevelLoggerNameFormattedMessageNullKeyValuesThrowable() {
		Level level = Level.INFO;
		String loggerName = "logger";
		String formattedMessage = null;
		KeyValues keyValues = KeyValues.of();
		Throwable throwable = null;
		var event = LogEvent.of(level, loggerName, formattedMessage, keyValues, throwable);
		assertFalse(event.hasMessage());
		assertNull(event.messageOrNull());
		assertEquals("null", event.message());
		assertInstanceOf(DefaultLogEvent.class, event);
		assertFreeze(event);
	}

	@Test
	void testOfLevelLoggerNameFormattedMessageThrowable() {
		Level level = Level.INFO;
		String loggerName = "logger";
		String formattedMessage = "Hello!";
		Throwable throwable = null;
		var event = LogEvent.of(level, loggerName, formattedMessage, throwable);
		assertNotNull(event);
		assertTrue(event.hasMessage());
		assertNotNull(event.messageOrNull());
		assertNull(event.throwableOrNull());
		assertTrue(event.keyValues().isEmpty());
		assertEquals(Level.INFO, event.level());
		assertInstanceOf(DefaultLogEvent.class, event);
		StringBuilder sb = new StringBuilder();
		event.formattedMessage(sb);
		assertEquals("Hello!", sb.toString());
		sb.setLength(0);
		event.formattedMessage(sb, "NOT EXPECTED");
		assertFreeze(event);
	}

	@Test
	void testOfLevelLoggerNameFormattedMessageNullThrowable() {
		Level level = Level.INFO;
		String loggerName = "logger";
		String formattedMessage = "Hello!";
		Throwable throwable = null;
		var event = LogEvent.of(level, loggerName, formattedMessage, throwable);
		assertNotNull(event);
		assertNull(event.throwableOrNull());
		assertTrue(event.keyValues().isEmpty());
		assertEquals(Level.INFO, event.level());
		assertInstanceOf(DefaultLogEvent.class, event);
		StringBuilder sb = new StringBuilder();
		event.formattedMessage(sb);
		assertEquals("Hello!", sb.toString());
		sb.setLength(0);
		event.formattedMessage(sb, "NOT EXPECTED");
		assertFreeze(event);
	}

	@Test
	void testOfLevelLoggerNameMessageKeyValuesMessageFormatterArg1() {
		Level level = Level.INFO;
		String loggerName = "logger";
		String message = "Hello {}!";
		KeyValues keyValues = KeyValues.of(Map.of("k1", "v1"));
		LogMessageFormatter messageFormatter = LogMessageFormatter.StandardMessageFormatter.SLF4J;
		Integer arg1 = 1;
		var event = LogEvent.of(level, loggerName, message, keyValues, messageFormatter, arg1);
		assertNotNull(event);
		assertTrue(event.hasMessage());
		assertNotNull(event.messageOrNull());
		assertNull(event.throwableOrNull());
		assertFalse(event.keyValues().isEmpty());
		String v = event.keyValues().getValueOrNull("k1");
		assertEquals("v1", v);
		assertEquals(Level.INFO, event.level());
		assertInstanceOf(OneArgLogEvent.class, event);
		var sb = new StringBuilder();
		event.formattedMessage(sb);
		assertEquals("Hello 1!", sb.toString());
		assertFreeze(event);
	}

	@Test
	void testOfLevelLoggerNameMessageNullKeyValuesMessageFormatterArg1() {
		Level level = Level.INFO;
		String loggerName = "logger";
		String message = null;
		KeyValues keyValues = KeyValues.of(Map.of("k1", "v1"));
		LogMessageFormatter messageFormatter = LogMessageFormatter.StandardMessageFormatter.SLF4J;
		Integer arg1 = 1;
		var event = LogEvent.of(level, loggerName, message, keyValues, messageFormatter, arg1);

		assertNotNull(event);
		assertFalse(event.hasMessage());
		assertNull(event.messageOrNull());
		assertEquals("null", event.message());
		assertInstanceOf(DefaultLogEvent.class, event);
		assertFalse(event.keyValues().isEmpty());
		String v = event.keyValues().getValueOrNull("k1");
		assertEquals("v1", v);
		assertEquals(Level.INFO, event.level());
		// We expect the args to be ignored because message was null
		assertInstanceOf(DefaultLogEvent.class, event);
		var sb = new StringBuilder();
		event.formattedMessage(sb, "MISSING EXPECTED");
		assertEquals("MISSING EXPECTED", sb.toString());
		assertFreeze(event);
	}

	@Test
	void testOfLevelLoggerNameMessageKeyValuesMessageFormatterArg1Arg2() {
		Level level = Level.INFO;
		String loggerName = "logger";
		String message = "Hello {} {}!";
		KeyValues keyValues = KeyValues.of(Map.of("k1", "v1"));
		LogMessageFormatter messageFormatter = LogMessageFormatter.StandardMessageFormatter.SLF4J;
		Integer arg1 = 1;
		URI arg2 = URI.create("https://jstach.io");
		var event = LogEvent.of(level, loggerName, message, keyValues, messageFormatter, arg1, arg2);
		assertNotNull(event);
		assertTrue(event.hasMessage());
		assertNotNull(event.messageOrNull());
		assertNull(event.throwableOrNull());
		assertFalse(event.keyValues().isEmpty());
		String v = event.keyValues().getValueOrNull("k1");
		assertEquals("v1", v);
		assertEquals(Level.INFO, event.level());
		assertInstanceOf(TwoArgLogEvent.class, event);
		var sb = new StringBuilder();
		event.formattedMessage(sb);
		assertEquals("Hello 1 https://jstach.io!", sb.toString());
		assertFreeze(event);
	}

	@Test
	void testOfLevelLoggerNameMessageNullKeyValuesMessageFormatterArg1Arg2() {
		Level level = Level.INFO;
		String loggerName = "logger";
		String message = null;
		KeyValues keyValues = KeyValues.of(Map.of("k1", "v1"));
		LogMessageFormatter messageFormatter = LogMessageFormatter.StandardMessageFormatter.SLF4J;
		Integer arg1 = 1;
		URI arg2 = URI.create("https://jstach.io");
		var event = LogEvent.of(level, loggerName, message, keyValues, messageFormatter, arg1, arg2);
		assertNotNull(event);
		assertFalse(event.hasMessage());
		assertNull(event.messageOrNull());
		assertNull(event.throwableOrNull());
		assertFalse(event.keyValues().isEmpty());
		String v = event.keyValues().getValueOrNull("k1");
		assertEquals("v1", v);
		assertEquals(Level.INFO, event.level());
		assertInstanceOf(DefaultLogEvent.class, event);
		var sb = new StringBuilder();
		event.formattedMessage(sb);
		assertEquals("null", sb.toString());
		assertFreeze(event);
	}

	@Test
	void testOfArgsZero() {
		Level level = Level.INFO;
		String loggerName = "logger";
		String message = "Hello!";
		KeyValues keyValues = KeyValues.of(Map.of("k1", "v1"));
		LogMessageFormatter messageFormatter = LogMessageFormatter.StandardMessageFormatter.SLF4J;
		var event = LogEvent.ofArgs(level, loggerName, message, keyValues, messageFormatter, new @Nullable Object[] {});
		assertNotNull(event);
		assertTrue(event.hasMessage());
		assertNotNull(event.messageOrNull());
		assertNull(event.throwableOrNull());
		assertFalse(event.keyValues().isEmpty());
		String v = event.keyValues().getValueOrNull("k1");
		assertEquals("v1", v);
		assertEquals(Level.INFO, event.level());
		assertInstanceOf(DefaultLogEvent.class, event);
		var sb = new StringBuilder();
		event.formattedMessage(sb);
		assertEquals("Hello!", sb.toString());
		assertFreeze(event);
	}

	@Test
	void testOfArgs1() {
		Level level = Level.INFO;
		String loggerName = "logger";
		String message = "Hello {}!";
		KeyValues keyValues = KeyValues.of(Map.of("k1", "v1"));
		LogMessageFormatter messageFormatter = LogMessageFormatter.StandardMessageFormatter.SLF4J;
		Integer arg1 = 1;
		var event = LogEvent.ofArgs(level, loggerName, message, keyValues, messageFormatter,
				new @Nullable Object[] { arg1 });
		assertNotNull(event);
		assertTrue(event.hasMessage());
		assertNotNull(event.messageOrNull());
		assertNull(event.throwableOrNull());
		assertFalse(event.keyValues().isEmpty());
		String v = event.keyValues().getValueOrNull("k1");
		assertEquals("v1", v);
		assertEquals(Level.INFO, event.level());
		assertInstanceOf(OneArgLogEvent.class, event);
		var sb = new StringBuilder();
		event.formattedMessage(sb);
		assertEquals("Hello 1!", sb.toString());
		assertFreeze(event);
	}

	@Test
	void testOfArgs2() {
		Level level = Level.INFO;
		String loggerName = "logger";
		String message = "Hello {} {}!";
		KeyValues keyValues = KeyValues.of(Map.of("k1", "v1"));
		LogMessageFormatter messageFormatter = LogMessageFormatter.StandardMessageFormatter.SLF4J;
		Integer arg1 = 1;
		URI arg2 = null;
		var event = LogEvent.ofArgs(level, loggerName, message, keyValues, messageFormatter,
				new @Nullable Object[] { arg1, arg2 });
		assertNotNull(event);
		assertTrue(event.hasMessage());
		assertNotNull(event.messageOrNull());
		assertNull(event.throwableOrNull());
		assertFalse(event.keyValues().isEmpty());
		String v = event.keyValues().getValueOrNull("k1");
		assertEquals("v1", v);
		assertEquals(Level.INFO, event.level());
		assertInstanceOf(TwoArgLogEvent.class, event);
		var sb = new StringBuilder();
		event.formattedMessage(sb);
		assertEquals("Hello 1 null!", sb.toString());
		assertFreeze(event);
	}

	@Test
	void testOfArgs3() {
		Level level = Level.INFO;
		String loggerName = "logger";
		String message = "Hello {} {} {}!";
		KeyValues keyValues = KeyValues.of(Map.of("k1", "v1"));
		LogMessageFormatter messageFormatter = LogMessageFormatter.StandardMessageFormatter.SLF4J;
		Integer arg1 = 1;
		URI arg2 = null;
		String arg3 = "nonnull";
		var event = LogEvent.ofArgs(level, loggerName, message, keyValues, messageFormatter,
				new @Nullable Object[] { arg1, arg2, arg3 });
		assertNotNull(event);
		assertTrue(event.hasMessage());
		assertNotNull(event.messageOrNull());
		assertNull(event.throwableOrNull());
		assertFalse(event.keyValues().isEmpty());
		String v = event.keyValues().getValueOrNull("k1");
		assertEquals("v1", v);
		assertEquals(Level.INFO, event.level());
		assertInstanceOf(ArrayArgLogEvent.class, event);
		var sb = new StringBuilder();
		event.formattedMessage(sb);
		assertEquals("Hello 1 null nonnull!", sb.toString());
		assertFreeze(event);
	}

	@Test
	void testOfAllThreeArgs() {
		Level level = Level.INFO;
		String loggerName = "logger";
		String message = "Hello {} {} {}!";
		KeyValues keyValues = KeyValues.of(Map.of("k1", "v1"));
		LogMessageFormatter messageFormatter = LogMessageFormatter.StandardMessageFormatter.SLF4J;
		Integer arg1 = 1;
		URI arg2 = null;
		String arg3 = "nonnull";
		Instant timestamp = Instant.EPOCH;
		String threadName = "main";
		long threadId = 0;
		Throwable throwable = new IllegalArgumentException("bad");
		List<@Nullable Object> args = Arrays.asList(arg1, arg2, arg3);
		var event = LogEvent.ofAll(timestamp, threadName, threadId, level, loggerName, message, keyValues, throwable,
				messageFormatter, args);
		assertNotNull(event);
		assertTrue(event.hasMessage());
		assertNotNull(event.messageOrNull());
		if (event.throwableOrNull() == null) {
			fail("throwable should not be null");
		}
		assertFalse(event.keyValues().isEmpty());
		String v = event.keyValues().getValueOrNull("k1");
		assertEquals("v1", v);
		assertEquals(Level.INFO, event.level());
		assertInstanceOf(ArrayArgLogEvent.class, event);
		var sb = new StringBuilder();
		event.formattedMessage(sb);
		assertEquals("Hello 1 null nonnull!", sb.toString());
		assertFreeze(event);
	}

	@Test
	void testOfAllZeroArgs() {
		Level level = Level.INFO;
		String loggerName = "logger";
		String message = "Hello!";
		KeyValues keyValues = KeyValues.of(Map.of("k1", "v1"));
		LogMessageFormatter messageFormatter = LogMessageFormatter.StandardMessageFormatter.SLF4J;
		Instant timestamp = Instant.EPOCH;
		String threadName = "main";
		long threadId = 0;
		Throwable throwable = new IllegalArgumentException("bad");
		List<@Nullable Object> args = Arrays.asList();
		var event = LogEvent.ofAll(timestamp, threadName, threadId, level, loggerName, message, keyValues, throwable,
				messageFormatter, args);
		assertNotNull(event);
		if (event.throwableOrNull() == null) {
			fail("throwable should not be null");
		}
		assertFalse(event.keyValues().isEmpty());
		String v = event.keyValues().getValueOrNull("k1");
		assertEquals("v1", v);
		assertEquals(Level.INFO, event.level());
		assertInstanceOf(DefaultLogEvent.class, event);
		var sb = new StringBuilder();
		event.formattedMessage(sb);
		assertEquals("Hello!", sb.toString());
		assertFreeze(event);
	}

	@Test
	void testOfAllOneArg() {
		Level level = Level.INFO;
		String loggerName = "logger";
		String message = "Hello {}!";
		KeyValues keyValues = KeyValues.of(Map.of("k1", "v1"));
		LogMessageFormatter messageFormatter = LogMessageFormatter.StandardMessageFormatter.SLF4J;
		Integer arg1 = 1;
		Instant timestamp = Instant.EPOCH;
		String threadName = "main";
		long threadId = 0;
		Throwable throwable = new IllegalArgumentException("bad");
		List<@Nullable Object> args = Arrays.asList(arg1);
		var event = LogEvent.ofAll(timestamp, threadName, threadId, level, loggerName, message, keyValues, throwable,
				messageFormatter, args);
		assertNotNull(event);
		if (event.throwableOrNull() == null) {
			fail("throwable should not be null");
		}
		assertFalse(event.keyValues().isEmpty());
		String v = event.keyValues().getValueOrNull("k1");
		assertEquals("v1", v);
		assertEquals(Level.INFO, event.level());
		assertInstanceOf(OneArgLogEvent.class, event);
		var sb = new StringBuilder();
		event.formattedMessage(sb);
		assertEquals("Hello 1!", sb.toString());
		assertFreeze(event);
	}

	@Test
	void testOfAllTwoArg() {
		Level level = Level.INFO;
		String loggerName = "logger";
		String message = "Hello {} {}!";
		KeyValues keyValues = KeyValues.of(Map.of("k1", "v1"));
		LogMessageFormatter messageFormatter = LogMessageFormatter.StandardMessageFormatter.SLF4J;
		Integer arg1 = 1;
		URI arg2 = null;
		Instant timestamp = Instant.EPOCH;
		String threadName = "main";
		long threadId = 0;
		Throwable throwable = new IllegalArgumentException("bad");
		List<@Nullable Object> args = Arrays.asList(arg1, arg2);
		var event = LogEvent.ofAll(timestamp, threadName, threadId, level, loggerName, message, keyValues, throwable,
				messageFormatter, args);
		assertNotNull(event);
		if (event.throwableOrNull() == null) {
			fail("throwable should not be null");
		}
		assertFalse(event.keyValues().isEmpty());
		String v = event.keyValues().getValueOrNull("k1");
		assertEquals("v1", v);
		assertEquals(Level.INFO, event.level());
		assertInstanceOf(TwoArgLogEvent.class, event);
		var sb = new StringBuilder();
		event.formattedMessage(sb);
		assertEquals("Hello 1 null!", sb.toString());
		assertFreeze(event);
	}

	// Checker has junit stub inccorrect
	private static void assertNotNull(@Nullable Object o) {
		if (o == null) {
			fail("o should not be null");
		}
	}

	void assertFreeze(LogEvent event) {
		var e = event.freeze(Instant.EPOCH);
		if (e.keyValues() instanceof MutableKeyValues) {
			fail("key values should be immutable");
		}
		assertEquals(Instant.EPOCH, e.timestamp());
		String message = e.message();
		StringBuilder sb = new StringBuilder();
		event.formattedMessage(sb);
		assertEquals(message, sb.toString(), "message should be formatted when frozen");
		assertEquals(e.hasMessage(), event.hasMessage(), "event should retain message nullability");

		assertCaller(event);
	}

	void assertCaller(LogEvent event) {
		Caller caller = Caller.ofDepthOrNull(0);
		if (caller == null) {
			fail("Caller");
			throw new IllegalStateException();
		}
		var e = LogEvent.withCaller(event, caller);
		assertInstanceOf(StackFrameLogEvent.class, e);
		var c = e.callerOrNull();
		if (c == null) {
			fail("Caller");
			throw new IllegalStateException();
		}
		assertInstanceOf(StackFrameCallerInfo.class, c);
		var frozen = e.freeze();
		c = frozen.callerOrNull();
		if (c == null) {
			fail("Caller");
			throw new IllegalStateException();
		}
		assertInstanceOf(StackFrameLogEvent.class, frozen);
		assertInstanceOf(FrozenCallerInfo.class, c);

	}

}

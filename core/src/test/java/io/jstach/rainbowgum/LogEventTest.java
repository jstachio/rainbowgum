package io.jstach.rainbowgum;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import java.lang.System.Logger.Level;
import java.net.URI;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;

import io.jstach.rainbowgum.KeyValues.MutableKeyValues;
import io.jstach.rainbowgum.LogEvent.Caller;

class LogEventTest {

	@Test
	void testOfLevelLoggerNameFormattedMessageKeyValuesThrowable() {
		Instant timestamp = Instant.EPOCH;
		String threadName = "main";
		long threadId = 0;
		Level level = Level.INFO;
		String loggerName = "logger";
		String formattedMessage = "Hello!";
		KeyValues keyValues = KeyValues.of();
		Throwable throwable = null;
		var event = LogEvent.of(timestamp, threadName, threadId, level, loggerName, formattedMessage, keyValues,
				throwable);
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
		Instant timestamp = Instant.EPOCH;
		String threadName = "main";
		long threadId = 0;
		Level level = Level.INFO;
		String loggerName = "logger";
		String formattedMessage = null;
		KeyValues keyValues = KeyValues.of();
		Throwable throwable = null;
		var event = LogEvent.of(timestamp, threadName, threadId, level, loggerName, formattedMessage, keyValues,
				throwable);
		assertFalse(event.hasMessage());
		assertNull(event.messageOrNull());
		assertEquals("null", event.message());
		assertInstanceOf(DefaultLogEvent.class, event);
		assertFreeze(event);
	}

	@Test
	void testOfLevelLoggerNameMessageKeyValuesMessageFormatterArg1() {
		Instant timestamp = Instant.EPOCH;
		String threadName = "main";
		long threadId = 0;
		Level level = Level.INFO;
		String loggerName = "logger";
		String message = "Hello {}!";
		KeyValues keyValues = KeyValues.of(Map.of("k1", "v1"));
		LogMessageFormatter messageFormatter = LogMessageFormatter.StandardMessageFormatter.SLF4J;
		Integer arg1 = 1;
		var event = LogEvent.ofOneArg(timestamp, threadName, threadId, level, loggerName, message, keyValues,
				messageFormatter, arg1);
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
		Instant timestamp = Instant.EPOCH;
		String threadName = "main";
		long threadId = 0;
		Level level = Level.INFO;
		String loggerName = "logger";
		String message = null;
		KeyValues keyValues = KeyValues.of(Map.of("k1", "v1"));
		LogMessageFormatter messageFormatter = LogMessageFormatter.StandardMessageFormatter.SLF4J;
		Integer arg1 = 1;
		var event = LogEvent.ofOneArg(timestamp, threadName, threadId, level, loggerName, message, keyValues,
				messageFormatter, arg1);

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
		Instant timestamp = Instant.EPOCH;
		String threadName = "main";
		long threadId = 0;
		Level level = Level.INFO;
		String loggerName = "logger";
		String message = "Hello {} {}!";
		KeyValues keyValues = KeyValues.of(Map.of("k1", "v1"));
		LogMessageFormatter messageFormatter = LogMessageFormatter.StandardMessageFormatter.SLF4J;
		Integer arg1 = 1;
		URI arg2 = URI.create("https://jstach.io");
		var event = LogEvent.ofTwoArgs(timestamp, threadName, threadId, level, loggerName, message, keyValues,
				messageFormatter, arg1, arg2);
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
		Instant timestamp = Instant.EPOCH;
		String threadName = "main";
		long threadId = 0;
		Level level = Level.INFO;
		String loggerName = "logger";
		String message = null;
		KeyValues keyValues = KeyValues.of(Map.of("k1", "v1"));
		LogMessageFormatter messageFormatter = LogMessageFormatter.StandardMessageFormatter.SLF4J;
		Integer arg1 = 1;
		URI arg2 = URI.create("https://jstach.io");
		var event = LogEvent.ofTwoArgs(timestamp, threadName, threadId, level, loggerName, message, keyValues,
				messageFormatter, arg1, arg2);
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
		Instant timestamp = Instant.EPOCH;
		String threadName = "main";
		long threadId = 0;
		Level level = Level.INFO;
		String loggerName = "logger";
		String message = "Hello!";
		KeyValues keyValues = KeyValues.of(Map.of("k1", "v1"));
		LogMessageFormatter messageFormatter = LogMessageFormatter.StandardMessageFormatter.SLF4J;
		var event = LogEvent.ofAll(timestamp, threadName, threadId, level, loggerName, message, keyValues, null,
				messageFormatter, new @Nullable Object[] {});
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
		Instant timestamp = Instant.EPOCH;
		String threadName = "main";
		long threadId = 0;
		Level level = Level.INFO;
		String loggerName = "logger";
		String message = "Hello {}!";
		KeyValues keyValues = KeyValues.of(Map.of("k1", "v1"));
		LogMessageFormatter messageFormatter = LogMessageFormatter.StandardMessageFormatter.SLF4J;
		Integer arg1 = 1;
		var event = LogEvent.ofAll(timestamp, threadName, threadId, level, loggerName, message, keyValues, null,
				messageFormatter, new @Nullable Object[] { arg1 });
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
		Instant timestamp = Instant.EPOCH;
		String threadName = "main";
		long threadId = 0;
		Level level = Level.INFO;
		String loggerName = "logger";
		String message = "Hello {} {}!";
		KeyValues keyValues = KeyValues.of(Map.of("k1", "v1"));
		LogMessageFormatter messageFormatter = LogMessageFormatter.StandardMessageFormatter.SLF4J;
		Integer arg1 = 1;
		URI arg2 = null;
		var event = LogEvent.ofAll(timestamp, threadName, threadId, level, loggerName, message, keyValues, null,
				messageFormatter, new @Nullable Object[] { arg1, arg2 });
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
		Instant timestamp = Instant.EPOCH;
		String threadName = "main";
		long threadId = 0;
		Level level = Level.INFO;
		String loggerName = "logger";
		String message = "Hello {} {} {}!";
		KeyValues keyValues = KeyValues.of(Map.of("k1", "v1"));
		LogMessageFormatter messageFormatter = LogMessageFormatter.StandardMessageFormatter.SLF4J;
		Integer arg1 = 1;
		URI arg2 = null;
		String arg3 = "nonnull";
		var event = LogEvent.ofAll(timestamp, threadName, threadId, level, loggerName, message, keyValues, null,
				messageFormatter, new @Nullable Object[] { arg1, arg2, arg3 });
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

	@Test
	void withCallerKeepsAMissingMessage() {
		var event = LogEvent.of(Instant.EPOCH, "main", 0, Level.WARNING, "logger", null, KeyValues.of(), null);
		var caller = Caller.ofDepthOrNull(0);
		if (caller == null) {
			fail("Caller");
			throw new IllegalStateException();
		}
		var e = LogEvent.withCaller(event, caller);
		assertFalse(e.hasMessage());
		assertNull(e.messageOrNull());
		var sb = new StringBuilder();
		e.formattedMessage(sb, "fallback");
		assertEquals("fallback", sb.toString());
		assertFalse(e.freeze().hasMessage());
	}

	@Test
	void frozenEventsKeepTheMessageAndSnapshotArguments() {
		var arguments = new StringBuilder("before");
		var keyValues = MutableKeyValues.of().add("state", "before");
		var formatter = LogMessageFormatter.StandardMessageFormatter.SLF4J;
		var events = List.of(
				LogEvent.ofOneArg(Instant.EPOCH, "main", 1, Level.INFO, "app", "value {}", keyValues, formatter,
						arguments),
				LogEvent.ofTwoArgs(Instant.EPOCH, "main", 1, Level.INFO, "app", "value {} {}", keyValues, formatter,
						arguments, "two"),
				LogEvent.ofAll(Instant.EPOCH, "main", 1, Level.INFO, "app", "value {} {} {}", keyValues, null,
						formatter, new Object[] { arguments, "two", "three" }));
		var frozen = events.stream().map(LogEvent::freeze).toList();
		arguments.replace(0, arguments.length(), "after");
		keyValues.add("state", "after");
		var caller = LogEvent.Caller.ofDepthOrNull(0);
		if (caller == null) {
			fail("Caller");
			throw new IllegalStateException();
		}
		var sb = new StringBuilder();
		for (var event : frozen) {
			assertSame(event, event.freeze());
			for (var e : List.of(event, event.freeze(Instant.MAX),
					LogEvent.withCaller(LogEvent.withLevel(event, Level.WARNING), caller).freeze(),
					LogEvent.rewrite(event, new LogEvent.Rewriter() {
					}))) {
				sb.append(e.level()).append(" ").append(e.message()).append(" | ");
				e.formattedMessage(sb);
				sb.append(" | ").append(e.keyValues().copyToMap()).append("\n");
			}
		}
		assertEquals("""
				INFO value {} | value before | {state=before}
				INFO value {} | value before | {state=before}
				WARNING value {} | value before | {state=before}
				INFO value before | value before | {state=before}
				INFO value {} {} | value before two | {state=before}
				INFO value {} {} | value before two | {state=before}
				WARNING value {} {} | value before two | {state=before}
				INFO value before two | value before two | {state=before}
				INFO value {} {} {} | value before two three | {state=before}
				INFO value {} {} {} | value before two three | {state=before}
				WARNING value {} {} {} | value before two three | {state=before}
				INFO value before two three | value before two three | {state=before}
				""", sb.toString());
	}

	@Test
	void withLevelKeepsEverythingButTheLevel() {
		var keyValues = MutableKeyValues.of().add("k1", "v1");
		var throwable = new RuntimeException("expected");
		var event = LogEvent.ofAll(Instant.EPOCH, "main", 0, Level.WARNING, "logger", "Hello {}!", keyValues, throwable,
				LogMessageFormatter.StandardMessageFormatter.SLF4J, new Object[] { 1 });
		var caller = Caller.ofDepthOrNull(0);
		if (caller == null) {
			fail("Caller");
			throw new IllegalStateException();
		}
		event = LogEvent.withCaller(event, caller);
		var info = LogEvent.withLevel(event, Level.INFO);
		assertInstanceOf(LevelLogEvent.class, info);
		assertEquals(Level.INFO, info.level());
		var sb = new StringBuilder();
		info.formattedMessage(sb);
		assertEquals("Hello 1!", sb.toString());
		assertEquals("Hello {}!", info.message());
		assertSame(event.timestamp(), info.timestamp());
		assertEquals("main", info.threadName());
		assertEquals("logger", info.loggerName());
		assertSame(keyValues, info.keyValues());
		assertSame(throwable, assertInstanceOf(RuntimeException.class, info.throwableOrNull()));
		assertSame(caller, assertInstanceOf(Caller.class, info.callerOrNull()));
		assertFreeze(info);
		var frozen = info.freeze();
		assertEquals(Level.INFO, frozen.level());
		assertEquals(Level.INFO, info.freeze(Instant.EPOCH).level());
		assertEquals(Level.INFO, LogEvent.withCaller(info, caller).level());
	}

	@Test
	void withLevelKeepsAMissingMessage() {
		var event = LogEvent.of(Instant.EPOCH, "main", 0, Level.WARNING, "logger", null, KeyValues.of(), null);
		var info = LogEvent.withLevel(event, Level.INFO);
		assertFalse(info.hasMessage());
		assertNull(info.messageOrNull());
		var sb = new StringBuilder();
		info.formattedMessage(sb, "fallback");
		assertEquals("fallback", sb.toString());
	}

	@Test
	void withLevelDoesNotWrapWhenTheLevelIsUnchangedOrAlreadyWrapped() {
		var event = TestLogEventFactory.of().event(Level.WARNING);
		assertSame(event, LogEvent.withLevel(event, Level.WARNING));
		var info = LogEvent.withLevel(event, Level.INFO);
		assertSame(info, LogEvent.withLevel(info, Level.INFO));
		assertSame(event, LogEvent.withLevel(info, Level.WARNING));
		var debug = (LevelLogEvent) LogEvent.withLevel(info, Level.DEBUG);
		assertSame(event, debug.event());
		assertEquals(Level.DEBUG, debug.level());
		var frozen = event.freeze();
		var frozenInfo = LogEvent.withLevel(frozen, Level.INFO);
		assertSame(frozenInfo, frozenInfo.freeze());
	}

	@Test
	void withLevelRejectsAllAndOff() {
		var event = TestLogEventFactory.of().event(Level.WARNING);
		var e = assertThrows(IllegalArgumentException.class, () -> LogEvent.withLevel(event, Level.OFF));
		assertEquals("An event cannot have level OFF.", e.getMessage());
		e = assertThrows(IllegalArgumentException.class, () -> LogEvent.withLevel(event, Level.ALL));
		assertEquals("An event cannot have level ALL.", e.getMessage());
	}

	@Test
	void rewriteRewritesTheMessageAndKeepsEverythingElse() {
		var keyValues = MutableKeyValues.of().add("k1", "v1");
		var throwable = new RuntimeException("expected");
		var event = LogEvent.ofAll(Instant.EPOCH, "main", 0, Level.WARNING, "logger", "Card {}!", keyValues, throwable,
				LogMessageFormatter.StandardMessageFormatter.SLF4J, new Object[] { "4111111111111111" });
		var rewritten = LogEvent.rewrite(event, new LogEvent.Rewriter() {
			@Override
			public void formattedMessage(LogEvent event, StringBuilder sb) {
				var original = new StringBuilder();
				event.formattedMessage(original);
				sb.append(original.toString().replaceAll("[0-9](?=[0-9]{4})", "*"));
			}
		});
		assertInstanceOf(RewrittenLogEvent.class, rewritten);
		var sb = new StringBuilder();
		rewritten.formattedMessage(sb);
		assertEquals("Card ************1111!", sb.toString());
		assertEquals("Card ************1111!", rewritten.message());
		assertEquals("Card ************1111!", rewritten.messageOrNull());
		assertTrue(rewritten.hasMessage());
		sb.setLength(0);
		rewritten.formattedMessage(sb, "fallback");
		assertEquals("Card ************1111!", sb.toString());
		assertEquals(Level.WARNING, rewritten.level());
		assertEquals("logger", rewritten.loggerName());
		assertSame(keyValues, rewritten.keyValues());
		assertSame(throwable, assertInstanceOf(RuntimeException.class, rewritten.throwableOrNull()));
		assertFreeze(rewritten);
		assertEquals("Card ************1111!", rewritten.freeze().message());
		assertEquals("Card ************1111!", rewritten.freeze(Instant.EPOCH).message());
	}

	@Test
	void rewriteAddsAKeyValueAndDropsTheThrowable() {
		var event = LogEvent.of(Instant.EPOCH, "main", 0, Level.WARNING, "logger", "Hello!",
				MutableKeyValues.of().add("k1", "v1"), new RuntimeException("expected"));
		var rewritten = LogEvent.rewrite(event, new LogEvent.Rewriter() {
			@Override
			public KeyValues keyValues(LogEvent event) {
				return KeyValues.merge(event.keyValues(), KeyValues.of(Map.of("k2", "v2")));
			}

			@Override
			public @Nullable Throwable throwableOrNull(LogEvent event) {
				return null;
			}
		});
		assertEquals(Map.of("k1", "v1", "k2", "v2"), rewritten.keyValues().copyToMap());
		assertNull(rewritten.throwableOrNull());
		assertEquals("Hello!", rewritten.message());
		assertNull(rewritten.freeze().throwableOrNull());
		assertEquals(Map.of("k1", "v1", "k2", "v2"), rewritten.freeze().keyValues().copyToMap());
	}

	@Test
	void rewriteKeepsAMissingMessage() {
		var event = LogEvent.of(Instant.EPOCH, "main", 0, Level.WARNING, "logger", null, KeyValues.of(), null);
		var rewritten = LogEvent.rewrite(event, new LogEvent.Rewriter() {
		});
		assertFalse(rewritten.hasMessage());
		assertNull(rewritten.messageOrNull());
		var sb = new StringBuilder();
		rewritten.formattedMessage(sb, "fallback");
		assertEquals("fallback", sb.toString());
	}

	@Test
	void rewriteCanAddAMissingMessage() {
		var original = LogEvent.of(Instant.EPOCH, "main", 1, Level.INFO, "app", null, KeyValues.of(), null);
		var rewritten = LogEvent.rewrite(original, new LogEvent.Rewriter() {
			@Override
			public boolean hasMessage(LogEvent event) {
				return true;
			}

			@Override
			public void formattedMessage(LogEvent event, StringBuilder sb) {
				sb.append("replacement");
			}
		});
		for (var event : List.of(rewritten, rewritten.freeze(), rewritten.freeze(Instant.MAX),
				LogEvent.withLevel(rewritten, Level.ERROR).freeze())) {
			assertTrue(event.hasMessage());
			assertEquals("replacement", event.message());
			assertEquals("replacement", event.messageOrNull());
			var sb = new StringBuilder();
			event.formattedMessage(sb, "fallback");
			assertEquals("replacement", sb.toString());
		}
		assertFalse(original.hasMessage());
	}

	@Test
	void rewriteCanRemoveAMessage() {
		var original = TestLogEventFactory.of().event(Level.INFO);
		var rewritten = LogEvent.rewrite(original, new LogEvent.Rewriter() {
			@Override
			public boolean hasMessage(LogEvent event) {
				return false;
			}
		});
		for (var event : List.of(rewritten, rewritten.freeze(), rewritten.freeze(Instant.MAX))) {
			assertFalse(event.hasMessage());
			assertNull(event.messageOrNull());
			var sb = new StringBuilder();
			event.formattedMessage(sb, "fallback");
			assertEquals("fallback", sb.toString());
		}
		assertTrue(original.hasMessage());
	}

	@Test
	void rewriteFormatsTheMessageEvenWhenNotRewritten() {
		var event = LogEvent.ofAll(Instant.EPOCH, "main", 0, Level.WARNING, "logger", "Hello {}!", KeyValues.of(), null,
				LogMessageFormatter.StandardMessageFormatter.SLF4J, new Object[] { 1 });
		var rewritten = LogEvent.rewrite(event, new LogEvent.Rewriter() {
		});
		assertEquals("Hello {}!", event.message());
		assertEquals("Hello 1!", rewritten.message());
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
		assertEquals(event.message(), e.message(), "message should stay unformatted when frozen");
		StringBuilder expected = new StringBuilder();
		event.formattedMessage(expected);
		StringBuilder actual = new StringBuilder();
		e.formattedMessage(actual);
		assertEquals(expected.toString(), actual.toString(), "formatted message should survive freezing");
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

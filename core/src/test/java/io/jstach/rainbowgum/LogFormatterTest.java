package io.jstach.rainbowgum;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.System.Logger.Level;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;

import org.eclipse.jdt.annotation.Nullable;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import io.jstach.rainbowgum.LogFormatter.LevelFormatter;
import io.jstach.rainbowgum.LogFormatter.ThrowableFormatter;
import io.jstach.rainbowgum.LogFormatter.TimestampFormatter;

class LogFormatterTest {

	@Test
	@SuppressWarnings("StringSplitter")
	void testThrowable() {
		Throwable t = new RuntimeException("expected");
		StringBuilder sb = new StringBuilder();
		var event = TestEventBuilder.of().build(e -> e.throwable(t));
		LogFormatter.builder().throwable().build().format(sb, event);
		String actual = sb.toString().split("\n")[0];
		assertEquals("java.lang.RuntimeException: expected", actual);
	}

	private static StackTraceElement frame(String method, int line) {
		return frame("com.example.App", method, line);
	}

	private static StackTraceElement frame(String className, String method, int line) {
		return new StackTraceElement(className, method, "App.java", line);
	}

	private static String format(Throwable t, ThrowableFormatter formatter) {
		StringBuilder sb = new StringBuilder();
		formatter.formatThrowable(sb, t);
		return sb.toString();
	}

	private static ThrowableFormatter of(int maxLines, List<String> excludes) {
		return ThrowableFormatter.builder().maxLines(maxLines).excludes(excludes).build();
	}

	private static ThrowableFormatter of(int maxLines, List<String> excludes, boolean packagingData) {
		return ThrowableFormatter.builder().maxLines(maxLines).excludes(excludes).packagingData(packagingData).build();
	}

	@SuppressWarnings("StringSplitter")
	private static String[] lines(String s) {
		return s.split(System.lineSeparator());
	}

	@Test
	void testMaxLinesTruncatesAndCountsCorrectly() {
		var t = new RuntimeException("boom");
		t.setStackTrace(new StackTraceElement[] { frame("a", 1), frame("b", 2), frame("c", 3), frame("d", 4) });

		String actual = format(t, of(2, List.of()));
		String[] lines = lines(actual);

		assertEquals("java.lang.RuntimeException: boom", lines[0]);
		assertEquals("\tat com.example.App.a(App.java:1)", lines[1]);
		assertEquals("\tat com.example.App.b(App.java:2)", lines[2]);
		assertEquals("\t... 2 frames truncated", lines[3]);
		assertEquals(4, lines.length);
	}

	@Test
	void testMaxLinesUnlimitedMatchesFullStack() {
		var t = new RuntimeException("boom");
		var frames = new StackTraceElement[] { frame("a", 1), frame("b", 2), frame("c", 3) };
		t.setStackTrace(frames);

		String actual = format(t, of(Integer.MAX_VALUE, List.of()));
		String[] lines = lines(actual);

		assertEquals(1 + frames.length, lines.length);
		for (int i = 0; i < frames.length; i++) {
			assertEquals("\tat " + frames[i], lines[i + 1]);
		}
	}

	@Test
	void testZeroMaxLinesPrintsOnlyHeaderAndTruncationNote() {
		var t = new RuntimeException("boom");
		t.setStackTrace(new StackTraceElement[] { frame("a", 1), frame("b", 2) });

		String actual = format(t, of(0, List.of()));
		String[] lines = lines(actual);

		assertEquals("java.lang.RuntimeException: boom", lines[0]);
		assertEquals("\t... 2 frames truncated", lines[1]);
		assertEquals(2, lines.length);
	}

	@Test
	void testExcludesFilterFramesAndDoNotCountAgainstMaxLines() {
		var t = new RuntimeException("boom");
		t.setStackTrace(new StackTraceElement[] { frame("keepA", 1), frame("noisyReflect", 2), frame("keepB", 3) });

		String actual = format(t, of(2, List.of("noisyReflect")));
		String[] lines = lines(actual);

		assertEquals("java.lang.RuntimeException: boom", lines[0]);
		assertEquals("\tat com.example.App.keepA(App.java:1)", lines[1]);
		assertEquals("\tat com.example.App.keepB(App.java:3)", lines[2]);
		assertEquals(3, lines.length, "excluded frame should not appear and should not trigger truncation");
	}

	@Test
	void testCauseChainElidesCommonFramesWhenNotTruncated() {
		var common = new StackTraceElement[] { frame("shared1", 10), frame("shared2", 11) };
		var causeFrames = new StackTraceElement[] { frame("causeOnly", 5), common[0], common[1] };
		var outerFrames = new StackTraceElement[] { frame("outerOnly", 20), common[0], common[1] };

		var cause = new RuntimeException("root cause");
		cause.setStackTrace(causeFrames);
		var outer = new RuntimeException("wrapper", cause);
		outer.setStackTrace(outerFrames);

		String actual = format(outer, of(Integer.MAX_VALUE, List.of()));
		String[] lines = lines(actual);

		assertEquals("java.lang.RuntimeException: wrapper", lines[0]);
		assertEquals("\tat com.example.App.outerOnly(App.java:20)", lines[1]);
		assertEquals("\tat com.example.App.shared1(App.java:10)", lines[2]);
		assertEquals("\tat com.example.App.shared2(App.java:11)", lines[3]);
		assertEquals("Caused by: java.lang.RuntimeException: root cause", lines[4]);
		assertEquals("\tat com.example.App.causeOnly(App.java:5)", lines[5]);
		assertEquals("\t... 2 more", lines[6]);
		assertEquals(7, lines.length);
	}

	@Test
	void testMaxLinesSuppressesCommonFrameNoteWhenTruncationAlreadyHappened() {
		var common = new StackTraceElement[] { frame("shared1", 10), frame("shared2", 11) };
		var causeFrames = new StackTraceElement[] { frame("c1", 1), frame("c2", 2), common[0], common[1] };
		var outerFrames = new StackTraceElement[] { frame("outerOnly", 20), common[0], common[1] };

		var cause = new RuntimeException("root cause");
		cause.setStackTrace(causeFrames);
		var outer = new RuntimeException("wrapper", cause);
		outer.setStackTrace(outerFrames);

		String actual = format(outer, of(1, List.of()));
		String[] lines = lines(actual);

		assertEquals("Caused by: java.lang.RuntimeException: root cause", lines[3]);
		assertEquals("\tat com.example.App.c1(App.java:1)", lines[4]);
		assertEquals("\t... 1 frames truncated", lines[5],
				"common-frame elision already reduced the considered range to [c1, c2] before maxLines applies");
		assertFalse(actual.contains("more"), "should not also print the common-frame elision note");
	}

	static final class CyclicThrowable extends RuntimeException {

		private static final long serialVersionUID = 1L;

		private transient @Nullable Throwable causeOverride;

		CyclicThrowable(String message) {
			super(message, null, false, false);
		}

		void setCauseUnsafe(Throwable t) {
			this.causeOverride = t;
		}

		@Override
		public synchronized @Nullable Throwable getCause() {
			return causeOverride;
		}

	}

	@Test
	void testCircularCauseChainDoesNotInfiniteLoop() {
		var a = new CyclicThrowable("a");
		a.setStackTrace(new StackTraceElement[] { frame("a", 1) });
		var b = new CyclicThrowable("b");
		b.setStackTrace(new StackTraceElement[] { frame("b", 2) });
		a.setCauseUnsafe(b);
		b.setCauseUnsafe(a);

		String actual = format(a, of(Integer.MAX_VALUE, List.of()));
		assertTrue(actual.contains("CIRCULAR REFERENCE"), "expected circular reference guard to trigger:\n" + actual);
	}

	@Test
	void testBuilderWithNoCustomizationReturnsDefaultFormatter() {
		assertSame(ThrowableFormatter.of(), ThrowableFormatter.builder().build(),
				"a builder left at its defaults should not construct a StandardThrowableFormatter");
	}

	@Test
	void testPackagingDataOffByDefault() {
		var t = new RuntimeException("boom");
		t.setStackTrace(new StackTraceElement[] { frame("a", 1) });

		String actual = format(t, of(Integer.MAX_VALUE, List.of()));
		String[] lines = lines(actual);
		assertEquals("\tat com.example.App.a(App.java:1)", lines[1]);
	}

	@Test
	void testPackagingDataIsNaForUnresolvableClass() {
		var t = new RuntimeException("boom");
		t.setStackTrace(new StackTraceElement[] { frame("com.example.DoesNotExist", "a", 1) });

		String actual = format(t, of(Integer.MAX_VALUE, List.of(), true));
		String[] lines = lines(actual);
		assertEquals("\tat com.example.DoesNotExist.a(App.java:1) [na:na]", lines[1]);
	}

	@Test
	void testPackagingDataResolvesJdkNamedModule() {
		var t = new RuntimeException("boom");
		t.setStackTrace(new StackTraceElement[] { frame("java.lang.String", "valueOf", 1) });

		String actual = format(t, of(Integer.MAX_VALUE, List.of(), true));
		String[] lines = lines(actual);
		assertTrue(lines[1].startsWith("\tat java.lang.String.valueOf(App.java:1) [java.base:"),
				"expected java.base module packaging data:\n" + actual);
	}

	@Test
	void testPackagingDataResolvesClasspathClass() {
		var t = new RuntimeException("boom");
		t.setStackTrace(new StackTraceElement[] { frame(LogFormatterTest.class.getName(), "test", 1) });

		String actual = format(t, of(Integer.MAX_VALUE, List.of(), true));
		String[] lines = lines(actual);
		assertFalse(lines[1].endsWith("[na:na]"), "expected this test's own class to be resolvable:\n" + actual);
	}

	private static String formatTimestamp(TimestampFormatter formatter, Instant instant) {
		StringBuilder sb = new StringBuilder();
		formatter.formatTimestamp(sb, instant);
		return sb.toString();
	}

	@Test
	void testTimestampFormatterOfIsTTLLTimeOnly() {
		Instant instant = Instant.parse("2023-11-14T22:13:20.123Z");
		assertEquals("22:13:20.123", formatTimestamp(TimestampFormatter.of(), instant));
	}

	@Test
	void testTimestampFormatterOfISOHasFixedMillisecondWidth() {
		Instant instant = Instant.parse("2023-11-14T22:13:20.5Z");
		assertEquals("2023-11-14T22:13:20.500Z", formatTimestamp(TimestampFormatter.ofISO(), instant));
	}

	@Test
	void testTimestampFormatterOfMicrosPadsToThreeDigits() {
		var formatter = TimestampFormatter.ofMicros();
		// nanos=123456 -> micros-over-millis = 123
		assertEquals("123", formatTimestamp(formatter, Instant.ofEpochSecond(0, 123_456)));
		// nanos=12345 -> micros-over-millis = 12, zero padded to "012"
		assertEquals("012", formatTimestamp(formatter, Instant.ofEpochSecond(0, 12_345)));
		// nanos=1234 -> micros-over-millis = 1, zero padded to "001"
		assertEquals("001", formatTimestamp(formatter, Instant.ofEpochSecond(0, 1_234)));
	}

	@Test
	void testTimestampFormatterOfDateTimeFormatterWithoutCaching() {
		var formatter = TimestampFormatter.of(DateTimeFormatter.ISO_INSTANT);
		Instant a = Instant.parse("2023-11-14T22:13:20.100Z");
		Instant b = Instant.parse("2023-11-14T22:13:20.200Z");
		assertEquals(DateTimeFormatter.ISO_INSTANT.format(a), formatTimestamp(formatter, a));
		assertEquals(DateTimeFormatter.ISO_INSTANT.format(b), formatTimestamp(formatter, b));
	}

	@Test
	void testTimestampFormatterOfDateTimeFormatterWithCaching() {
		var formatter = TimestampFormatter.of(DateTimeFormatter.ISO_INSTANT, true);
		Instant sameMillisA = Instant.parse("2023-11-14T22:13:20.100Z");
		Instant sameMillisB = sameMillisA.plusNanos(1);
		Instant differentMillis = sameMillisA.plusMillis(1);

		String first = formatTimestamp(formatter, sameMillisA);
		String cached = formatTimestamp(formatter, sameMillisB);
		String reformatted = formatTimestamp(formatter, differentMillis);

		assertEquals(DateTimeFormatter.ISO_INSTANT.format(sameMillisA), first);
		assertEquals(first, cached, "same millisecond should reuse the cached formatted string");
		assertNotEquals(first, reformatted, "a different millisecond should reformat");
		assertEquals(DateTimeFormatter.ISO_INSTANT.format(differentMillis), reformatted);
	}

	@Test
	void testDefaultInstantFormatterToString() {
		assertEquals("TTLL", TimestampFormatter.of().toString());
		assertEquals("ISO", TimestampFormatter.ofISO().toString());
	}

	@ParameterizedTest
	@EnumSource(Level.class)
	void testLevelFormatterToStringMapsToSlf4jStyleNames(Level level) {
		String expected = switch (level) {
			case ALL, TRACE -> "TRACE";
			case DEBUG -> "DEBUG";
			case INFO -> "INFO";
			case WARNING -> "WARN";
			case ERROR, OFF -> "ERROR";
		};
		assertEquals(expected, LevelFormatter.toString(level));
	}

	@ParameterizedTest
	@EnumSource(Level.class)
	void testLevelFormatterRightPaddedMapsToFixedWidthNames(Level level) {
		String expected = switch (level) {
			case ALL, TRACE -> "TRACE";
			case DEBUG -> "DEBUG";
			case INFO -> "INFO ";
			case WARNING -> "WARN ";
			case ERROR, OFF -> "ERROR";
		};
		String actual = LevelFormatter.rightPadded(level);
		assertEquals(expected, actual);
		assertEquals(5, actual.length(), "right padded level names should all be the same width");
	}

	@Test
	void testLevelFormatterOfFormatsEvent() {
		StringBuilder sb = new StringBuilder();
		var event = TestEventBuilder.of().level(Level.WARNING).build();
		LevelFormatter.of().format(sb, event);
		assertEquals("WARN", sb.toString());
	}

	@Test
	void testLevelFormatterOfRightPaddedFormatsEvent() {
		StringBuilder sb = new StringBuilder();
		var event = TestEventBuilder.of().level(Level.INFO).build();
		LevelFormatter.ofRightPadded().format(sb, event);
		assertEquals("INFO ", sb.toString());
	}

	@Test
	void testEncodedKeyValuesFormatsAllKeysPercentEncoded() {
		StringBuilder sb = new StringBuilder();
		var kvs = KeyValues.MutableKeyValues.of().add("a b", "c/d").add("nullValued", null);
		var event = TestEventBuilder.of().build(b -> b.keyValues(kvs));
		LogFormatter.builder().encodedKeyValues().build().format(sb, event);
		assertEquals("a%20b=c%2Fd&nullValued", sb.toString());
	}

	@Test
	void testEncodedKeyValueUsesFallbackWhenKeyMissing() {
		StringBuilder sb = new StringBuilder();
		var event = TestEventBuilder.of().build();
		LogFormatter.builder().encodedKeyValue("missing", "fallback value").build().format(sb, event);
		assertEquals("missing=fallback%20value", sb.toString());
	}

	@Test
	void testEncodedKeyValueUsesActualValueWhenPresent() {
		StringBuilder sb = new StringBuilder();
		var kvs = KeyValues.of(Map.of("k", "v"));
		var event = TestEventBuilder.of().build(b -> b.keyValues(kvs));
		LogFormatter.builder().encodedKeyValue("k", "fallback").build().format(sb, event);
		assertEquals("k=v", sb.toString());
	}

	@Test
	void testPadRightPadsShortString() {
		StringBuilder sb = new StringBuilder();
		LogFormatter.padRight(sb, "ab", 5);
		assertEquals("ab   ", sb.toString());
	}

	@Test
	void testPadRightTruncatesLongString() {
		StringBuilder sb = new StringBuilder();
		LogFormatter.padRight(sb, "abcdef", 3);
		assertEquals("abc", sb.toString());
	}

	@Test
	void testPadRightExactLengthIsUnchanged() {
		StringBuilder sb = new StringBuilder();
		LogFormatter.padRight(sb, "abc", 3);
		assertEquals("abc", sb.toString());
	}

	@Test
	void testPadLeftPadsShortString() {
		StringBuilder sb = new StringBuilder();
		LogFormatter.padLeft(sb, "ab", 5);
		assertEquals("   ab", sb.toString());
	}

	@Test
	void testPadLeftTruncatesLongString() {
		StringBuilder sb = new StringBuilder();
		LogFormatter.padLeft(sb, "abcdef", 3);
		assertEquals("abc", sb.toString());
	}

	@Test
	void testPadRightLargePaddingExercisesSpacePadLoop() {
		StringBuilder sb = new StringBuilder();
		LogFormatter.padRight(sb, "x", 40);
		assertEquals("x" + " ".repeat(39), sb.toString());
	}

	@Test
	void testIsNoopOrNullWithNull() {
		assertTrue(LogFormatter.isNoopOrNull(null));
	}

	@Test
	void testIsNoopOrNullWithNoopFormatter() {
		assertTrue(LogFormatter.isNoopOrNull(LogFormatter.noop()));
	}

	@Test
	void testIsNoopOrNullWithRealFormatter() {
		assertFalse(LogFormatter.isNoopOrNull(LogFormatter.builder().message().build()));
	}

	@Test
	void testLogFormatterOfReturnsSameLambdaAndFormatsThroughIt() {
		LogFormatter.EventFormatter e = (sb, event) -> sb.append("custom");
		var formatter = LogFormatter.of(e);
		assertSame(e, formatter);
		StringBuilder sb = new StringBuilder();
		formatter.format(sb, TestEventBuilder.of().build());
		assertEquals("custom", sb.toString());
	}

}

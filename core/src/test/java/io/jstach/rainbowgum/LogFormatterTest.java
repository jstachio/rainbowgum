package io.jstach.rainbowgum;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.eclipse.jdt.annotation.Nullable;
import org.junit.jupiter.api.Test;

import io.jstach.rainbowgum.LogFormatter.ThrowableFormatter;

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

	@SuppressWarnings("StringSplitter")
	private static String[] lines(String s) {
		return s.split(System.lineSeparator());
	}

	@Test
	void testMaxLinesTruncatesAndCountsCorrectly() {
		var t = new RuntimeException("boom");
		t.setStackTrace(new StackTraceElement[] { frame("a", 1), frame("b", 2), frame("c", 3), frame("d", 4) });

		String actual = format(t, ThrowableFormatter.of(2, List.of()));
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

		String actual = format(t, ThrowableFormatter.of(Integer.MAX_VALUE, List.of()));
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

		String actual = format(t, ThrowableFormatter.of(0, List.of()));
		String[] lines = lines(actual);

		assertEquals("java.lang.RuntimeException: boom", lines[0]);
		assertEquals("\t... 2 frames truncated", lines[1]);
		assertEquals(2, lines.length);
	}

	@Test
	void testExcludesFilterFramesAndDoNotCountAgainstMaxLines() {
		var t = new RuntimeException("boom");
		t.setStackTrace(new StackTraceElement[] { frame("keepA", 1), frame("noisyReflect", 2), frame("keepB", 3) });

		String actual = format(t, ThrowableFormatter.of(2, List.of("noisyReflect")));
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

		String actual = format(outer, ThrowableFormatter.of(Integer.MAX_VALUE, List.of()));
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

		String actual = format(outer, ThrowableFormatter.of(1, List.of()));
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

		String actual = format(a, ThrowableFormatter.of(Integer.MAX_VALUE, List.of()));
		assertTrue(actual.contains("CIRCULAR REFERENCE"), "expected circular reference guard to trigger:\n" + actual);
	}

	@Test
	void testPackagingDataOffByDefault() {
		var t = new RuntimeException("boom");
		t.setStackTrace(new StackTraceElement[] { frame("a", 1) });

		String actual = format(t, ThrowableFormatter.of(Integer.MAX_VALUE, List.of()));
		String[] lines = lines(actual);
		assertEquals("\tat com.example.App.a(App.java:1)", lines[1]);
	}

	@Test
	void testPackagingDataIsNaForUnresolvableClass() {
		var t = new RuntimeException("boom");
		t.setStackTrace(new StackTraceElement[] { frame("com.example.DoesNotExist", "a", 1) });

		String actual = format(t, ThrowableFormatter.of(Integer.MAX_VALUE, List.of(), true));
		String[] lines = lines(actual);
		assertEquals("\tat com.example.DoesNotExist.a(App.java:1) [na:na]", lines[1]);
	}

	@Test
	void testPackagingDataResolvesJdkNamedModule() {
		var t = new RuntimeException("boom");
		t.setStackTrace(new StackTraceElement[] { frame("java.lang.String", "valueOf", 1) });

		String actual = format(t, ThrowableFormatter.of(Integer.MAX_VALUE, List.of(), true));
		String[] lines = lines(actual);
		assertTrue(lines[1].startsWith("\tat java.lang.String.valueOf(App.java:1) [java.base:"),
				"expected java.base module packaging data:\n" + actual);
	}

	@Test
	void testPackagingDataResolvesClasspathClass() {
		var t = new RuntimeException("boom");
		t.setStackTrace(new StackTraceElement[] { frame(LogFormatterTest.class.getName(), "test", 1) });

		String actual = format(t, ThrowableFormatter.of(Integer.MAX_VALUE, List.of(), true));
		String[] lines = lines(actual);
		assertFalse(lines[1].endsWith("[na:na]"), "expected this test's own class to be resolvable:\n" + actual);
	}

}

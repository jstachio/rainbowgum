package io.jstach.rainbowgum;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import io.jstach.rainbowgum.LogFormatter.ThrowableFormatter;

/*
 * The two cyclic-reference tests below are adapted from logback's own
 * RootCauseFirstThrowableProxyConverterTest (cyclicCause/cyclicSuppressed) - same
 * construction (Exception(Exception) plus initCause/addSuppressed to form a real cycle)
 * and the same startsWith/relative-position style of assertion, since the exact frame
 * lines are this file's own real stack trace (not a synthetic one like the other tests
 * here) and therefore not worth golden-stringing byte for byte.
 */
class LogFormatterTest {

	/*
	 * root/mid/outer form a 3-level cause chain; root and mid deliberately share a
	 * trailing "shared" frame (root's wrapper is mid) so the "... N more" elision path is
	 * exercised too, not just ordering/captions. suppressedWrapper (with its own cause
	 * suppressedRoot) is attached to outer to exercise the "Suppressed: " caption landing
	 * on the root of a suppressed exception's own chain rather than the suppressed
	 * exception's own line - see RootCauseFirstThrowableFormatter's javadoc.
	 */
	private static Throwable buildChain() {
		var root = new RuntimeException("root cause");
		root.setStackTrace(new StackTraceElement[] { new StackTraceElement("com.example.App", "r1", "App.java", 1),
				new StackTraceElement("com.example.App", "shared", "App.java", 9), });
		var mid = new RuntimeException("mid wrapper", root);
		mid.setStackTrace(new StackTraceElement[] { new StackTraceElement("com.example.App", "m1", "App.java", 2),
				new StackTraceElement("com.example.App", "shared", "App.java", 9), });
		var outer = new RuntimeException("outer wrapper", mid);
		outer.setStackTrace(new StackTraceElement[] { new StackTraceElement("com.example.App", "o1", "App.java", 3) });
		var suppressedRoot = new RuntimeException("suppressed root");
		suppressedRoot
			.setStackTrace(new StackTraceElement[] { new StackTraceElement("com.example.App", "s1", "App.java", 5) });
		var suppressedWrapper = new RuntimeException("suppressed wrapper", suppressedRoot);
		suppressedWrapper
			.setStackTrace(new StackTraceElement[] { new StackTraceElement("com.example.App", "sw1", "App.java", 6) });
		outer.addSuppressed(suppressedWrapper);
		return outer;
	}

	@Test
	void testRootCauseFirstPrintsRootFirstThenWrapsBackUp() {
		var formatter = ThrowableFormatter.builder().rootCauseFirst(true).build();
		var sb = new StringBuilder();
		formatter.formatThrowable(sb, buildChain());
		assertEquals("""
				java.lang.RuntimeException: root cause
					at com.example.App.r1(App.java:1)
					... 1 more
				Wrapped by: java.lang.RuntimeException: mid wrapper
					at com.example.App.m1(App.java:2)
					at com.example.App.shared(App.java:9)
				Wrapped by: java.lang.RuntimeException: outer wrapper
					at com.example.App.o1(App.java:3)
					Suppressed: java.lang.RuntimeException: suppressed root
						at com.example.App.s1(App.java:5)
					Wrapped by: java.lang.RuntimeException: suppressed wrapper
						at com.example.App.sw1(App.java:6)
				""", sb.toString());
	}

	@Test
	void testDefaultOrderStillPrintsOutermostFirstForTheSameChain() {
		// Same chain as testRootCauseFirstPrintsRootFirstThenWrapsBackUp, default
		// (non-root-cause-first) order - confirms rootCauseFirst(true) actually changed
		// something rather than both paths coincidentally producing the same output.
		var formatter = ThrowableFormatter.builder().maxLines(Integer.MAX_VALUE).build();
		var sb = new StringBuilder();
		formatter.formatThrowable(sb, buildChain());
		assertEquals("""
				java.lang.RuntimeException: outer wrapper
					at com.example.App.o1(App.java:3)
					Suppressed: java.lang.RuntimeException: suppressed wrapper
						at com.example.App.sw1(App.java:6)
					Caused by: java.lang.RuntimeException: suppressed root
						at com.example.App.s1(App.java:5)
				Caused by: java.lang.RuntimeException: mid wrapper
					at com.example.App.m1(App.java:2)
					at com.example.App.shared(App.java:9)
				Caused by: java.lang.RuntimeException: root cause
					at com.example.App.r1(App.java:1)
					... 1 more
				""", sb.toString());
	}

	@Test
	void testRootCauseFirstWithNoCauseIsJustTheThrowableItself() {
		var formatter = ThrowableFormatter.builder().rootCauseFirst(true).build();
		var sb = new StringBuilder();
		var solo = new RuntimeException("alone");
		solo.setStackTrace(new StackTraceElement[] { new StackTraceElement("com.example.App", "a1", "App.java", 1) });
		formatter.formatThrowable(sb, solo);
		assertEquals("""
				java.lang.RuntimeException: alone
					at com.example.App.a1(App.java:1)
				""", sb.toString());
	}

	@Test
	void testRootCauseFirstCyclicCausePrintsCircularReferenceMarker() {
		var formatter = ThrowableFormatter.builder().rootCauseFirst(true).build();
		Exception e = new Exception("foo");
		Exception e2 = new Exception(e);
		e.initCause(e2);
		var sb = new StringBuilder();
		formatter.formatThrowable(sb, e);
		String result = sb.toString();
		assertTrue(result.startsWith("[CIRCULAR REFERENCE: java.lang.Exception: foo]"), result);
	}

	@Test
	void testRootCauseFirstCyclicSuppressedPrintsCircularReferenceBeforeWrapper() {
		var formatter = ThrowableFormatter.builder().rootCauseFirst(true).build();
		Exception e = new Exception("foo");
		Exception e2 = new Exception(e);
		e.addSuppressed(e2);
		var sb = new StringBuilder();
		formatter.formatThrowable(sb, e);
		String result = sb.toString();
		assertTrue(result.startsWith("java.lang.Exception: foo"), result);
		String circular = "Suppressed: [CIRCULAR REFERENCE: java.lang.Exception: foo]";
		String wrapped = "Wrapped by: java.lang.Exception: java.lang.Exception: foo";
		int circularIndex = result.indexOf(circular);
		int wrappedIndex = result.indexOf(wrapped);
		assertTrue(circularIndex >= 0 && wrappedIndex >= 0 && circularIndex < wrappedIndex, result);
	}

}

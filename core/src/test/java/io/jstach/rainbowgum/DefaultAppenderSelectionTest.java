package io.jstach.rainbowgum;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.function.IntSupplier;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import io.jstach.rainbowgum.LogAppender.AppenderFlag;
import io.jstach.rainbowgum.output.ListLogOutput;

/**
 * Exercises {@code DirectLogAppender#defaultAppender} directly - the JDK-version-gated
 * choice made when no {@link AppenderFlag} explicitly requests a buffer/lock strategy.
 * {@link AppenderAsModeFlagPermutationTest} covers the same dispatch as part of its full
 * flag-combination sweep, but forces a fixed JDK version throughout, so it never
 * exercises the pre-{@value AppenderLock#SYNCHRONIZED_DEFAULT_MIN_JDK_VERSION} branch.
 * This class covers both branches explicitly, plus that REENTRY_DROP/REENTRY_LOG actually
 * work on both - {@code SynchronizedThreadLocalBufferLogAppender} detects reentrancy via
 * {@link Thread#holdsLock(Object)} rather than {@code AppenderLock}, so it needs its own
 * behavioral coverage, not just a class-type assertion.
 */
class DefaultAppenderSelectionTest {

	final IntSupplier originalJdkFeatureVersionSupplier = AppenderLock.jdkFeatureVersionSupplier;

	ByteArrayOutputStream metaLogBytes = new ByteArrayOutputStream();

	PrintStream metaLogStream = new PrintStream(metaLogBytes);

	@BeforeEach
	void before() {
		MetaLog.output = () -> metaLogStream;
	}

	@AfterEach
	void after() {
		AppenderLock.jdkFeatureVersionSupplier = originalJdkFeatureVersionSupplier;
		MetaLog.output = () -> System.err;
	}

	@Test
	void modernJdkNoFlagsSelectsSynchronizedThreadLocalBuffer() {
		AppenderLock.jdkFeatureVersionSupplier = () -> AppenderLock.SYNCHRONIZED_DEFAULT_MIN_JDK_VERSION;
		var appender = appender(Set.of());
		assertInstanceOf(SynchronizedThreadLocalBufferLogAppender.class, appender);
	}

	@Test
	void olderJdkNoFlagsSelectsThreadLocalBuffer() {
		AppenderLock.jdkFeatureVersionSupplier = () -> AppenderLock.SYNCHRONIZED_DEFAULT_MIN_JDK_VERSION - 1;
		var appender = appender(Set.of());
		assertInstanceOf(ThreadLocalBufferLogAppender.class, appender);
	}

	@Test
	void modernJdkReentryDropWithNoOtherFlagSelectsSynchronizedThreadLocalBuffer() {
		AppenderLock.jdkFeatureVersionSupplier = () -> AppenderLock.SYNCHRONIZED_DEFAULT_MIN_JDK_VERSION;
		var appender = appender(EnumSet.of(AppenderFlag.REENTRY_DROP));
		assertInstanceOf(SynchronizedThreadLocalBufferLogAppender.class, appender);
	}

	@Test
	void modernJdkReentryLogWithNoOtherFlagSelectsSynchronizedThreadLocalBuffer() {
		AppenderLock.jdkFeatureVersionSupplier = () -> AppenderLock.SYNCHRONIZED_DEFAULT_MIN_JDK_VERSION;
		var appender = appender(EnumSet.of(AppenderFlag.REENTRY_LOG));
		assertInstanceOf(SynchronizedThreadLocalBufferLogAppender.class, appender);
	}

	@Test
	void synchronizedThreadLocalBufferReentryDropFlagDropsReentrantAppend() {
		AppenderLock.jdkFeatureVersionSupplier = () -> AppenderLock.SYNCHRONIZED_DEFAULT_MIN_JDK_VERSION;
		var output = new ListLogOutput();
		var testAppender = appender(EnumSet.of(AppenderFlag.REENTRY_DROP), output);
		assertInstanceOf(SynchronizedThreadLocalBufferLogAppender.class, testAppender);
		output.setConsumer((e, s) -> {
			// A naughty output that logs during its own write - should be dropped.
			testAppender.append(TestEventBuilder.of().build(b -> b.message("reentrant")));
		});
		testAppender.append(TestEventBuilder.of().build(b -> b.message("original")));
		assertEquals(List.of("original"), output.events().stream().map(e -> e.getKey().message()).toList());
	}

	@Test
	void synchronizedThreadLocalBufferReentryLogFlagDropsReentrantAppendAndLogsDiagnostic() {
		AppenderLock.jdkFeatureVersionSupplier = () -> AppenderLock.SYNCHRONIZED_DEFAULT_MIN_JDK_VERSION;
		var output = new ListLogOutput();
		var testAppender = appender(EnumSet.of(AppenderFlag.REENTRY_LOG), output);
		assertInstanceOf(SynchronizedThreadLocalBufferLogAppender.class, testAppender);
		output.setConsumer((e, s) -> {
			testAppender.append(TestEventBuilder.of().build(b -> b.message("reentrant")));
		});
		testAppender.append(TestEventBuilder.of().build(b -> b.message("original")));
		assertEquals(List.of("original"), output.events().stream().map(e -> e.getKey().message()).toList());
		String diagnostic = metaLogBytes.toString(StandardCharsets.UTF_8);
		assertTrue(diagnostic.contains("reentrant appender"), () -> "expected reentry diagnostic, got: " + diagnostic);
	}

	private static LogAppender appender(Set<AppenderFlag> flags) {
		return appender(flags, new ListLogOutput());
	}

	private static LogAppender appender(Set<AppenderFlag> flags, ListLogOutput output) {
		var encoder = LogEncoder.of(LogFormatter.builder().message().build());
		return DirectLogAppender.of("test", output, encoder, flags);
	}

}

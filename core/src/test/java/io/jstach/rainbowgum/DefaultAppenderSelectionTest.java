package io.jstach.rainbowgum;

import static org.junit.jupiter.api.Assertions.assertInstanceOf;

import java.util.EnumSet;
import java.util.Set;
import java.util.function.IntSupplier;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import io.jstach.rainbowgum.LogAppender.AppenderFlag;
import io.jstach.rainbowgum.output.ListLogOutput;

/**
 * Exercises {@code DirectLogAppender#defaultAppender} directly - the JDK-version-gated
 * choice made when no {@link AppenderFlag} explicitly requests a buffer/lock strategy.
 * {@link AppenderAsModeFlagPermutationTest} covers the same dispatch as part of its full
 * flag-combination sweep, but forces a fixed JDK version throughout, so it never
 * exercises the pre-{@value AppenderLock#SYNCHRONIZED_DEFAULT_MIN_JDK_VERSION} branch.
 * This class covers both branches, plus the REENTRY_DROP/REENTRY_LOG override,
 * explicitly.
 */
class DefaultAppenderSelectionTest {

	final IntSupplier originalJdkFeatureVersionSupplier = AppenderLock.jdkFeatureVersionSupplier;

	@AfterEach
	void after() {
		AppenderLock.jdkFeatureVersionSupplier = originalJdkFeatureVersionSupplier;
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
	void modernJdkReentryDropWithNoOtherFlagStillSelectsDefaultLogAppender() {
		AppenderLock.jdkFeatureVersionSupplier = () -> AppenderLock.SYNCHRONIZED_DEFAULT_MIN_JDK_VERSION;
		var appender = appender(EnumSet.of(AppenderFlag.REENTRY_DROP));
		assertInstanceOf(DefaultLogAppender.class, appender);
	}

	@Test
	void modernJdkReentryLogWithNoOtherFlagStillSelectsDefaultLogAppender() {
		AppenderLock.jdkFeatureVersionSupplier = () -> AppenderLock.SYNCHRONIZED_DEFAULT_MIN_JDK_VERSION;
		var appender = appender(EnumSet.of(AppenderFlag.REENTRY_LOG));
		assertInstanceOf(DefaultLogAppender.class, appender);
	}

	private static LogAppender appender(Set<AppenderFlag> flags) {
		var encoder = LogEncoder.of(LogFormatter.builder().message().build());
		return DirectLogAppender.of("test", new ListLogOutput(), encoder, flags);
	}

}

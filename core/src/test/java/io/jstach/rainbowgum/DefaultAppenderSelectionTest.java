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

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.junit.jupiter.api.parallel.Isolated;

import io.jstach.rainbowgum.LogAppender.AppenderFlag;
import io.jstach.rainbowgum.LogAppender.AppenderType;
import io.jstach.rainbowgum.output.ListLogOutput;

/**
 * Exercises {@link DirectLogAppender#of} directly for each {@link AppenderType}, plus
 * {@code LogProperties#GLOBAL_APPENDER_REENTRANT_LOCK_PROPERTY}'s guarantee that even an
 * explicit {@link AppenderType#SYNCHRONIZED_THREAD_LOCAL_BUFFER} request gets downgraded
 * when that global property is active, and that
 * {@code SynchronizedThreadLocalBufferLogAppender}'s reentry detection (via
 * {@link Thread#holdsLock(Object)} rather than a
 * {@code java.util.concurrent.locks.ReentrantLock}) still works when explicitly opted
 * into.
 */
/*
 * Mutates two shared static fields: MetaLog.output (see MetaLogTest's note - several
 * other test classes touch it too) and AbstractLogAppender.forceReentrantLockAppenders
 * (only this class touches that one, but its own methods still need to not race each
 * other). @Isolated covers the former, SAME_THREAD covers both.
 */
@Isolated
@Execution(ExecutionMode.SAME_THREAD)
class DefaultAppenderSelectionTest {

	final boolean originalForceReentrantLockAppenders = AbstractLogAppender.forceReentrantLockAppenders;

	ByteArrayOutputStream metaLogBytes = new ByteArrayOutputStream();

	PrintStream metaLogStream = new PrintStream(metaLogBytes);

	@BeforeEach
	void before() {
		MetaLog.output = () -> metaLogStream;
	}

	@AfterEach
	void after() {
		AbstractLogAppender.forceReentrantLockAppenders = originalForceReentrantLockAppenders;
		MetaLog.output = () -> System.err;
	}

	@Test
	void lockThreadLocalBufferTypeSelectsLockThreadLocalBuffer() {
		var appender = appender(AppenderType.LOCK_THREAD_LOCAL_BUFFER, Set.of());
		assertInstanceOf(LockThreadLocalBufferLogAppender.class, appender);
	}

	@Test
	void globalForceReentrantLockDowngradesExplicitSynchronizedType() {
		AbstractLogAppender.forceReentrantLockAppenders = true;
		var appender = appender(AppenderType.SYNCHRONIZED_THREAD_LOCAL_BUFFER, Set.of());
		assertInstanceOf(LockThreadLocalBufferLogAppender.class, appender);
	}

	@Test
	void reentryDropWithLockThreadLocalBufferType() {
		var appender = appender(AppenderType.LOCK_THREAD_LOCAL_BUFFER, EnumSet.of(AppenderFlag.REENTRY_DROP));
		assertInstanceOf(LockThreadLocalBufferLogAppender.class, appender);
	}

	@Test
	void reentryLogWithLockThreadLocalBufferType() {
		var appender = appender(AppenderType.LOCK_THREAD_LOCAL_BUFFER, EnumSet.of(AppenderFlag.REENTRY_LOG));
		assertInstanceOf(LockThreadLocalBufferLogAppender.class, appender);
	}

	@Test
	void lockNewBufferTypeSelectsLockNewBuffer() {
		var appender = appender(AppenderType.LOCK_NEW_BUFFER, Set.of());
		assertInstanceOf(LockNewBufferLogAppender.class, appender);
	}

	@Test
	void synchronizedThreadLocalBufferReentryDropFlagDropsReentrantAppend() {
		var output = new ListLogOutput();
		var testAppender = appender(AppenderType.SYNCHRONIZED_THREAD_LOCAL_BUFFER,
				EnumSet.of(AppenderFlag.REENTRY_DROP), output);
		assertInstanceOf(SynchronizedThreadLocalBufferLogAppender.class, testAppender);
		output.setConsumer((e, s) -> {
			// A naughty output that logs during its own write - should be dropped.
			testAppender.append(TestLogEventFactory.of().event("reentrant"));
		});
		testAppender.append(TestLogEventFactory.of().event("original"));
		assertEquals(List.of("original"), output.events().stream().map(e -> e.getKey().message()).toList());
	}

	@Test
	void synchronizedThreadLocalBufferReentryLogFlagDropsReentrantAppendAndLogsDiagnostic() {
		var output = new ListLogOutput();
		var testAppender = appender(AppenderType.SYNCHRONIZED_THREAD_LOCAL_BUFFER, EnumSet.of(AppenderFlag.REENTRY_LOG),
				output);
		assertInstanceOf(SynchronizedThreadLocalBufferLogAppender.class, testAppender);
		output.setConsumer((e, s) -> {
			testAppender.append(TestLogEventFactory.of().event("reentrant"));
		});
		testAppender.append(TestLogEventFactory.of().event("original"));
		assertEquals(List.of("original"), output.events().stream().map(e -> e.getKey().message()).toList());
		String diagnostic = metaLogBytes.toString(StandardCharsets.UTF_8);
		assertTrue(diagnostic.contains("reentrant appender"), () -> "expected reentry diagnostic, got: " + diagnostic);
	}

	/*
	 * Built once, at class load - not per call inside appender(...) below. Constructing a
	 * LogConfig re-evaluates LogProperties#GLOBAL_APPENDER_REENTRANT_LOCK_PROPERTY and
	 * resets AbstractLogAppender.forceReentrantLockAppenders as a side effect (see
	 * LogConfig#applyGlobalAppenderReentrantLockProperty) - doing that on every
	 * appender() call would wipe out a test's own direct override of that field before
	 * DirectLogAppender.of gets a chance to read it.
	 */
	private static final LogConfig CONFIG = LogConfig.builder().build();

	private static final LogEncoder ENCODER = LogFormatter.builder()
		.message()
		.encoder()
		.build()
		.provide("test", CONFIG);

	private static LogAppender appender(AppenderType type, Set<AppenderFlag> flags) {
		return appender(type, flags, new ListLogOutput());
	}

	private static LogAppender appender(AppenderType type, Set<AppenderFlag> flags, ListLogOutput output) {
		return DirectLogAppender.of("test", output, ENCODER, type, flags, CONFIG.alerts(), CONFIG.metrics());
	}

}

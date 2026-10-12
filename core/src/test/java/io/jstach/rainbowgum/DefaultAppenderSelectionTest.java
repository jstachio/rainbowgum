package io.jstach.rainbowgum;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.junit.jupiter.api.parallel.Isolated;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import io.jstach.rainbowgum.LogAppender.AppenderFlag;
import io.jstach.rainbowgum.LogAppender.AppenderType;
import io.jstach.rainbowgum.output.ListLogOutput;

/**
 * Exercises {@link DirectLogAppender#of} directly for each {@link AppenderType}, plus
 * {@code LogProperties#GLOBAL_APPENDER_LOCK_PROPERTY}'s guarantee that even an explicit
 * {@link AppenderType#SYNCHRONIZED_THREAD_LOCAL_BUFFER} request gets downgraded when that
 * global is on, and that {@code SynchronizedThreadLocalBufferLogAppender}'s reentry
 * detection (via {@link Thread#holdsLock(Object)} rather than a
 * {@code java.util.concurrent.locks.ReentrantLock}) still works when explicitly opted
 * into.
 */
/*
 * Mutates MetaLog.output (see MetaLogTest's note - several other test classes touch it
 * too) and the native image system property.
 */
@Isolated
@Execution(ExecutionMode.SAME_THREAD)
class DefaultAppenderSelectionTest {

	final @Nullable String originalNativeImageCodeProperty = System
		.getProperty(AbstractLogAppender.NATIVE_IMAGE_CODE_PROPERTY);

	ByteArrayOutputStream metaLogBytes = new ByteArrayOutputStream();

	PrintStream metaLogStream = new PrintStream(metaLogBytes);

	@BeforeEach
	void before() {
		MetaLog.output = () -> metaLogStream;
	}

	@AfterEach
	void after() {
		// checker will not allow System.clearProperty(...) (see
		// RainbowGumServiceProviderTest's own note on this) - "" reads back as
		// not-"runtime" the same as a genuinely absent property does, which is all
		// isNativeImageRuntime() ever checks, so this restores the same effective state.
		System.setProperty(AbstractLogAppender.NATIVE_IMAGE_CODE_PROPERTY,
				originalNativeImageCodeProperty == null ? "" : originalNativeImageCodeProperty);
		MetaLog.output = () -> System.err;
	}

	@Test
	void lockThreadLocalBufferTypeSelectsLockThreadLocalBuffer() {
		var appender = appender(AppenderType.LOCK_THREAD_LOCAL_BUFFER, Set.of());
		assertInstanceOf(LockThreadLocalBufferLogAppender.class, appender);
	}

	@Test
	void reentrantAppenderLockDowngradesExplicitSynchronizedType() {
		var appender = appender(AppenderType.SYNCHRONIZED_THREAD_LOCAL_BUFFER, Set.of(), REENTRANT_LOCK);
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
	void globalForceNoThreadLocalDowngradesLockThreadLocalBufferType() {
		var appender = appender(AppenderType.LOCK_THREAD_LOCAL_BUFFER, Set.of(), NO_THREAD_LOCAL);
		assertInstanceOf(LockNewBufferLogAppender.class, appender);
	}

	@Test
	void globalForceNoThreadLocalDowngradesSynchronizedThreadLocalBufferType() {
		var appender = appender(AppenderType.SYNCHRONIZED_THREAD_LOCAL_BUFFER, Set.of(), NO_THREAD_LOCAL);
		assertInstanceOf(LockNewBufferLogAppender.class, appender);
	}

	@Test
	void globalForceNoThreadLocalLeavesExplicitReuseBufferAlone() {
		var appender = appender(AppenderType.REUSE_BUFFER, Set.of(), NO_THREAD_LOCAL);
		assertInstanceOf(ReuseBufferLogAppender.class, appender);
	}

	@Test
	void autoDetectSelectsLockThreadLocalBufferOutsideNativeImage() {
		// see after()'s own comment for why "" instead of System.clearProperty(...).
		System.setProperty(AbstractLogAppender.NATIVE_IMAGE_CODE_PROPERTY, "");
		var appender = appender(AppenderType.AUTO_DETECT, Set.of());
		assertInstanceOf(LockThreadLocalBufferLogAppender.class, appender);
	}

	@Test
	void autoDetectSelectsSynchronizedThreadLocalBufferInsideNativeImage() {
		System.setProperty(AbstractLogAppender.NATIVE_IMAGE_CODE_PROPERTY, "runtime");
		assertEquals(AppenderType.SYNCHRONIZED_THREAD_LOCAL_BUFFER,
				AbstractLogAppender.resolveAutoDetectAppenderType(AppenderType.AUTO_DETECT));
	}

	@Test
	void synchronizedAppenderLockUpgradesLockThreadLocalBufferType() {
		var appender = appender(AppenderType.LOCK_THREAD_LOCAL_BUFFER, Set.of(), SYNCHRONIZED_LOCK);
		assertInstanceOf(SynchronizedThreadLocalBufferLogAppender.class, appender);
	}

	@Test
	void synchronizedAppenderLockLeavesTypesWithoutASynchronizedVariantAlone() {
		assertInstanceOf(LockNewBufferLogAppender.class,
				appender(AppenderType.LOCK_NEW_BUFFER, Set.of(), SYNCHRONIZED_LOCK));
		assertInstanceOf(ReuseBufferLogAppender.class,
				appender(AppenderType.REUSE_BUFFER, Set.of(), SYNCHRONIZED_LOCK));
	}

	/*
	 * AUTO is REENTRANT before JDK 24 (JEP 491) and leaves the type alone from 24.
	 */
	@ParameterizedTest
	@CsvSource({ "21, LOCK_THREAD_LOCAL_BUFFER", "23, LOCK_THREAD_LOCAL_BUFFER", "24, SYNCHRONIZED_THREAD_LOCAL_BUFFER",
			"27, SYNCHRONIZED_THREAD_LOCAL_BUFFER" })
	void autoAppenderLockDependsOnTheJdk(int jdk, AppenderType expected) {
		assertEquals(expected, AbstractLogAppender.applyAppenderLock(AppenderType.SYNCHRONIZED_THREAD_LOCAL_BUFFER,
				LogConfig.Globals.AppenderLock.AUTO, jdk));
	}

	@Test
	void autoAppenderLockNeverChangesLockThreadLocalBuffer() {
		assertEquals(AppenderType.LOCK_THREAD_LOCAL_BUFFER, AbstractLogAppender
			.applyAppenderLock(AppenderType.LOCK_THREAD_LOCAL_BUFFER, LogConfig.Globals.AppenderLock.AUTO, 21));
	}

	@Test
	void autoDetectInsideNativeImageStillHonorsGlobalForceReentrantLock() {
		System.setProperty(AbstractLogAppender.NATIVE_IMAGE_CODE_PROPERTY, "runtime");
		var appender = appender(AppenderType.AUTO_DETECT, Set.of(), REENTRANT_LOCK);
		assertInstanceOf(LockThreadLocalBufferLogAppender.class, appender);
	}

	@Test
	void autoDetectInsideNativeImageStillHonorsGlobalForceNoThreadLocal() {
		System.setProperty(AbstractLogAppender.NATIVE_IMAGE_CODE_PROPERTY, "runtime");
		var appender = appender(AppenderType.AUTO_DETECT, Set.of(), NO_THREAD_LOCAL);
		assertInstanceOf(LockNewBufferLogAppender.class, appender);
	}

	@Test
	void forbiddenThreadLocalPropertyDowngradesThroughTheBuilder() {
		var props = LogProperties.builder().fromProperties("logging.global.threadLocal=forbid").build();
		var config = LogConfig.builder().properties(props).build();
		assertInstanceOf(LockNewBufferLogAppender.class, builderAppender(null, config));
	}

	/*
	 * The globals belong to each config, so one config's setting does not change another
	 * config's appenders.
	 */
	@Test
	void globalsDoNotLeakBetweenConfigs() {
		var props = LogProperties.builder().fromProperties("logging.global.threadLocal=forbid").build();
		LogConfig.builder().properties(props).build();
		assertInstanceOf(LockThreadLocalBufferLogAppender.class, builderAppender(null, CONFIG));
	}

	@Test
	void unspecifiedAppenderTypeStaysLockThreadLocalBufferWhenGlobalOptimizeDisabled() {
		var appender = builderAppender(null);
		assertInstanceOf(LockThreadLocalBufferLogAppender.class, appender);
	}

	@Test
	void unspecifiedAppenderTypeResolvesThroughAutoDetectOutsideNativeImageWhenGlobalOptimizeEnabled() {
		System.setProperty(AbstractLogAppender.NATIVE_IMAGE_CODE_PROPERTY, "");
		var appender = builderAppender(null, OPTIMIZE);
		assertInstanceOf(LockThreadLocalBufferLogAppender.class, appender);
	}

	@Test
	void unspecifiedAppenderTypeResolvesThroughAutoDetectInsideNativeImageWhenGlobalOptimizeEnabled() {
		// The default AUTO appender lock does not use synchronized before JDK 24.
		assumeTrue(Runtime.version().feature() >= 24);
		System.setProperty(AbstractLogAppender.NATIVE_IMAGE_CODE_PROPERTY, "runtime");
		var appender = builderAppender(null, OPTIMIZE);
		assertInstanceOf(SynchronizedThreadLocalBufferLogAppender.class, appender);
	}

	@Test
	void explicitAppenderTypeWinsOverGlobalOptimize() {
		System.setProperty(AbstractLogAppender.NATIVE_IMAGE_CODE_PROPERTY, "runtime");
		var appender = builderAppender(AppenderType.REUSE_BUFFER, OPTIMIZE);
		assertInstanceOf(ReuseBufferLogAppender.class, appender);
	}

	@Test
	void synchronizedThreadLocalBufferReentryDropFlagDropsReentrantAppend() {
		var output = new ListLogOutput();
		var testAppender = appender(AppenderType.SYNCHRONIZED_THREAD_LOCAL_BUFFER,
				EnumSet.of(AppenderFlag.REENTRY_DROP), output, SYNCHRONIZED_LOCK);
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
				output, SYNCHRONIZED_LOCK);
		assertInstanceOf(SynchronizedThreadLocalBufferLogAppender.class, testAppender);
		output.setConsumer((e, s) -> {
			testAppender.append(TestLogEventFactory.of().event("reentrant"));
		});
		testAppender.append(TestLogEventFactory.of().event("original"));
		assertEquals(List.of("original"), output.events().stream().map(e -> e.getKey().message()).toList());
		String diagnostic = metaLogBytes.toString(StandardCharsets.UTF_8);
		assertTrue(diagnostic.contains("reentrant appender"), () -> "expected reentry diagnostic, got: " + diagnostic);
	}

	private static final LogConfig CONFIG = LogConfig.builder().build();

	private static final LogConfig REENTRANT_LOCK = LogConfig.builder()
		.appenderLock(LogConfig.Globals.AppenderLock.REENTRANT)
		.build();

	/*
	 * For tests that need a synchronized appender whatever the JDK: the default AUTO lock
	 * does not use synchronized before JDK 24.
	 */
	private static final LogConfig SYNCHRONIZED_LOCK = LogConfig.builder()
		.appenderLock(LogConfig.Globals.AppenderLock.SYNCHRONIZED)
		.build();

	private static final LogConfig NO_THREAD_LOCAL = LogConfig.builder()
		.threadLocal(LogConfig.Globals.ThreadLocalMode.FORBID)
		.build();

	private static final LogConfig OPTIMIZE = LogConfig.builder().optimize(true).build();

	private static final LogEncoder ENCODER = LogFormatter.builder()
		.message()
		.encoder()
		.build()
		.provide("test", CONFIG);

	private static LogAppender appender(AppenderType type, Set<AppenderFlag> flags) {
		return appender(type, flags, new ListLogOutput());
	}

	private static LogAppender appender(AppenderType type, Set<AppenderFlag> flags, ListLogOutput output) {
		return appender(type, flags, output, CONFIG);
	}

	private static LogAppender appender(AppenderType type, Set<AppenderFlag> flags, ListLogOutput output,
			LogConfig config) {
		return DirectLogAppender.of("test", output, ENCODER, type, flags, config);
	}

	private static LogAppender appender(AppenderType type, Set<AppenderFlag> flags, LogConfig config) {
		return DirectLogAppender.of("test", new ListLogOutput(), ENCODER, type, flags, config);
	}

	/*
	 * Goes through the real LogAppender.Builder#build() path (unlike appender(...) above,
	 * which calls DirectLogAppender.of directly with an already-resolved type) - the only
	 * path that exercises the "no explicit appender type" default resolution
	 * LogProperties#GLOBAL_OPTIMIZE_PROPERTY changes.
	 */
	private static LogAppender builderAppender(@Nullable AppenderType type) {
		return builderAppender(type, CONFIG);
	}

	private static LogAppender builderAppender(@Nullable AppenderType type, LogConfig config) {
		var builder = LogAppender.builder("test").output(new ListLogOutput()).encoder(ENCODER);
		if (type != null) {
			builder.appenderType(type);
		}
		return builder.build().provide("test", config);
	}

}

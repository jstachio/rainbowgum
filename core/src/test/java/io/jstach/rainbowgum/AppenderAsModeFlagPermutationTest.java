package io.jstach.rainbowgum;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import io.jstach.rainbowgum.LogAppender.AppenderFlag;
import io.jstach.rainbowgum.LogAppender.Appenders;
import io.jstach.rainbowgum.LogAppenderFlagTest.CountingListLogOutput;
import io.jstach.rainbowgum.output.ListLogOutput;

/**
 * End-to-end permutation coverage across every {@link AppenderFlag} combination and both
 * {@link Appenders} "as" modes ({@link Appenders#asSingle()}, {@link Appenders#asList()})
 * - a baseline safety net that caught the {@code SHARED_APPENDER_LOCK}/
 * {@code asSingleSharedLock()} reentry inconsistency documented in
 * {@link AppenderAsModeReentryTest} before that whole shared-lock composite strategy was
 * removed. Appenders always keep their own independent lock now.
 * <p>
 * {@code asList()} has no real production caller today - only {@code asSingle()} is used
 * by the built-in DEFAULT/SYNC/ASYNC publisher factories (see
 * {@code LogPublisherRegistry.DefaultPublisherProviders}). It exists for a future
 * fanout-style publisher; {@link FanoutSyncLogPublisher} is a small synchronous test
 * publisher built to exercise it the way a real one eventually would - see that class for
 * details.
 */
class AppenderAsModeFlagPermutationTest {

	final java.util.function.IntSupplier originalJdkFeatureVersionSupplier = AppenderLock.jdkFeatureVersionSupplier;

	@BeforeEach
	void before() {
		// Deterministic regardless of which JDK actually runs the build - see
		// testPermutation's expectedAppenderClass computation below.
		AppenderLock.jdkFeatureVersionSupplier = () -> AppenderLock.SYNCHRONIZED_DEFAULT_MIN_JDK_VERSION;
	}

	@AfterEach
	void after() {
		AppenderLock.jdkFeatureVersionSupplier = originalJdkFeatureVersionSupplier;
	}

	enum AsMode {

		SINGLE, LIST;

	}

	record CombinationCase(AsMode mode, Set<AppenderFlag> flags) {
		@Override
		public String toString() {
			return mode + " " + flags;
		}
	}

	static Stream<Arguments> permutations() {
		List<Arguments> args = new ArrayList<>();
		for (var flags : powerSet(AppenderFlag.values())) {
			for (var mode : AsMode.values()) {
				args.add(Arguments.of(new CombinationCase(mode, flags)));
			}
		}
		return args.stream();
	}

	private static List<Set<AppenderFlag>> powerSet(AppenderFlag[] values) {
		List<Set<AppenderFlag>> result = new ArrayList<>();
		int n = values.length;
		for (int mask = 0; mask < (1 << n); mask++) {
			var set = EnumSet.noneOf(AppenderFlag.class);
			for (int i = 0; i < n; i++) {
				if ((mask & (1 << i)) != 0) {
					set.add(values[i]);
				}
			}
			result.add(set);
		}
		return result;
	}

	@ParameterizedTest
	@MethodSource("permutations")
	void testPermutation(CombinationCase testCase) {
		var mode = testCase.mode();
		var flags = testCase.flags();

		LogConfig config = LogConfig.builder().build();
		var outputA = new CountingListLogOutput();
		var outputB = new CountingListLogOutput();
		List<LogProvider<LogAppender>> providers = List.of(
				LogAppender.builder("a").encoder(LogFormatter.builder().message().encoder()).output(outputA).build(),
				LogAppender.builder("b").encoder(LogFormatter.builder().message().encoder()).output(outputB).build());
		var appenders = new Appenders("test-route", config, providers).flags(flags);

		LogPublisher publisher = switch (mode) {
			case SINGLE -> new DefaultSyncLogPublisher(appenders.asSingle());
			case LIST -> new FanoutSyncLogPublisher(appenders.asList());
		};

		publisher.start(config);
		try {
			publisher.log(TestEventBuilder.of().build(b -> b.message("hello")));
		}
		finally {
			publisher.close();
		}

		// Every appender in the group receives the event regardless of mode - that is
		// the whole point of asList()'s fanout publisher matching what asSingle()
		// already does internally.
		assertEquals(List.of("hello"), outputA.events().stream().map(e -> e.getValue()).toList());
		assertEquals(List.of("hello"), outputB.events().stream().map(e -> e.getValue()).toList());

		int expectedFlushes = flags.contains(AppenderFlag.DISABLE_IMMEDIATE_FLUSH) ? 0 : 1;
		assertEquals(expectedFlushes, outputA.flushCount, "outputA flush count");
		assertEquals(expectedFlushes, outputB.flushCount, "outputB flush count");

		Class<?> expectedAppenderClass;
		if (flags.contains(AppenderFlag.REUSE_BUFFER)) {
			expectedAppenderClass = ReuseBufferLogAppender.class;
		}
		else if (flags.contains(AppenderFlag.SYNCHRONIZED_THREAD_LOCAL_BUFFER)) {
			expectedAppenderClass = SynchronizedThreadLocalBufferLogAppender.class;
		}
		else if (flags.contains(AppenderFlag.THREAD_LOCAL_BUFFER)) {
			expectedAppenderClass = ThreadLocalBufferLogAppender.class;
		}
		else if (flags.contains(AppenderFlag.REENTRY_DROP) || flags.contains(AppenderFlag.REENTRY_LOG)) {
			// No buffer-strategy flag: falls to the default appender selection, which
			// defers to DefaultLogAppender when reentry detection was explicitly
			// requested, since neither ThreadLocalBuffer appender supports it.
			expectedAppenderClass = DefaultLogAppender.class;
		}
		else {
			// No buffer-strategy or reentry flag: the default appender selection - forced
			// to the modern-JDK branch above, so
			// SynchronizedThreadLocalBufferLogAppender.
			expectedAppenderClass = SynchronizedThreadLocalBufferLogAppender.class;
		}
		for (var direct : directAppenders(mode, publisher)) {
			assertInstanceOf(expectedAppenderClass, direct);
		}
	}

	private static List<DirectLogAppender> directAppenders(AsMode mode, LogPublisher publisher) {
		return switch (mode) {
			case SINGLE -> switch (((DefaultSyncLogPublisher) publisher).appender()) {
				case CompositeLogAppender c -> List.of(c.components());
				case DirectLogAppender d -> List.of(d);
				default -> throw new AssertionError("unexpected appender type");
			};
			case LIST ->
				((FanoutSyncLogPublisher) publisher).appenders().stream().map(a -> (DirectLogAppender) a).toList();
		};
	}

	/*
	 * The rest of this file drives Appenders directly (matching LogAppenderFlagTest's
	 * established style) rather than through a full RainbowGum route, since building 32
	 * full RainbowGum instances would be needlessly slow and the "as" modes are an
	 * Appenders-level concern anyway. This one test instead wires FanoutSyncLogPublisher
	 * in through a real route's PublisherFactory - exactly how a real fanout publisher
	 * would plug in - to confirm asList() genuinely works end-to-end through the normal
	 * RainbowGum bootstrap path, not just when driven directly.
	 */
	@Test
	void testFanoutPublisherPluggedInThroughARealRainbowGumRoute() {
		var outputA = new ListLogOutput();
		var outputB = new ListLogOutput();
		LogConfig config = LogConfig.builder().build();
		try (var gum = RainbowGum.builder(config).route(r -> {
			r.appender("a", a -> a.output(outputA).encoder(LogFormatter.builder().message().newline().encoder()));
			r.appender("b", a -> a.output(outputB).encoder(LogFormatter.builder().message().newline().encoder()));
			r.publisher((name, cfg, appenders) -> new FanoutSyncLogPublisher(appenders.asList()));
		}).build().start()) {
			gum.router().eventBuilder("test", System.Logger.Level.INFO).message("fanned out").log();
		}
		assertEquals("fanned out\n", outputA.toString());
		assertEquals("fanned out\n", outputB.toString());
	}

}

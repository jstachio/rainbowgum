package io.jstach.rainbowgum;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import io.jstach.rainbowgum.AppenderAsModeFlagPermutationTest.AsMode;
import io.jstach.rainbowgum.LogAppender.AppenderFlag;
import io.jstach.rainbowgum.LogAppender.Appenders;
import io.jstach.rainbowgum.output.ListLogOutput;

/**
 * REENTRY_DROP/REENTRY_LOG behave identically for a single appender (see
 * {@link LogAppenderFlagTest}), but the three {@link Appenders} "as" modes wire the
 * reentry-checking lock at different scopes when more than one appender is combined,
 * which changes what a <em>sibling</em> appender sees during a reentrant call:
 * <ul>
 * <li>{@code asSingleSharedLock()} ({@link CompositeLogAppender}) enforces the check
 * <strong>once</strong>, at the composite level, using one lock shared by all appenders -
 * a reentrant call is dropped in its entirety before it reaches any appender.</li>
 * <li>{@code asSingle()}'s default ({@link IndependentLockCompositeLogAppender}) and
 * {@code asList()} (via {@link FanoutSyncLogPublisher}, which loops the same way) both
 * give every appender its own independent lock and do no locking at the fan-out level
 * itself - so a reentrant call only gets dropped for the specific appender that's
 * reentering its own lock; every other appender still receives it, nested ahead of the
 * event that triggered the reentry in the first place.</li>
 * </ul>
 * This is exactly the kind of surprising, easy-to-break-silently behavior worth pinning
 * down before a {@link LogAppender} refactor.
 */
class AppenderAsModeReentryTest {

	ByteArrayOutputStream metaLogBytes = new ByteArrayOutputStream();

	PrintStream metaLogStream = new PrintStream(metaLogBytes);

	@BeforeEach
	void before() {
		MetaLog.output = () -> metaLogStream;
	}

	@AfterEach
	void after() {
		MetaLog.output = () -> System.err;
	}

	static Stream<Arguments> modesAndReentryFlags() {
		List<Arguments> args = new ArrayList<>();
		for (var mode : AsMode.values()) {
			for (var flag : List.of(AppenderFlag.REENTRY_DROP, AppenderFlag.REENTRY_LOG)) {
				args.add(Arguments.of(mode, flag));
			}
		}
		return args.stream();
	}

	@ParameterizedTest
	@MethodSource("modesAndReentryFlags")
	void testReentryBehaviorDiffersByMode(AsMode mode, AppenderFlag reentryFlag) {
		LogConfig config = LogConfig.builder().build();
		// naughty: logs a second event from within its own output write.
		var outputA = new ListLogOutput();
		// innocent bystander.
		var outputB = new ListLogOutput();

		LogPublisher[] publisherHolder = new LogPublisher[1];
		outputA.setConsumer((e, s) -> {
			if (e.message().equals("event1")) {
				publisherHolder[0].log(TestEventBuilder.of().build(b -> b.message("event2")));
			}
		});

		/*
		 * The reentry flag has to be set BOTH on each appender's own builder AND via
		 * Appenders.flags(...) to actually take effect across all three modes - found the
		 * hard way while writing this test, and worth pinning down since it is a
		 * genuinely confusing inconsistency in how flags flow to the lock that actually
		 * does the reentry check:
		 *
		 * - An individual DirectLogAppender's *lock* (as opposed to its flags field, used
		 * only for immediateFlush/REUSE_BUFFER selection) is fixed once, from whatever
		 * flags were on its builder at construction (LockLogAppender.withFlags always
		 * reuses `this.lock` unchanged, no matter what flags are merged in later) - so
		 * asSingle() (IndependentLockCompositeLogAppender, which merges flags
		 * per-appender via withFlags but never calls changeLock) and asList() (which
		 * never composites at all) only honor a REENTRY_DROP/REENTRY_LOG set on the
		 * *appender builder*; Appenders.flags(...) alone does nothing for them. -
		 * asSingleSharedLock() (CompositeLogAppender) does the opposite: it builds a
		 * brand new outer lock from Appenders.flags(...) and unconditionally replaces
		 * every inner appender's lock with an always-allow-reentry wrapper via
		 * changeLock(directLock) - so a REENTRY_DROP/REENTRY_LOG set only on the appender
		 * builder is silently discarded, and only Appenders.flags(...) works.
		 */
		List<LogProvider<LogAppender>> providers = List.of(
				LogAppender.builder("a")
					.encoder(LogFormatter.builder().message().encoder())
					.output(outputA)
					.flag(reentryFlag)
					.build(),
				LogAppender.builder("b")
					.encoder(LogFormatter.builder().message().encoder())
					.output(outputB)
					.flag(reentryFlag)
					.build());
		var appenders = new Appenders("test-route", config, providers).flags(Set.of(reentryFlag));

		LogPublisher publisher = switch (mode) {
			case SINGLE -> new DefaultSyncLogPublisher(appenders.asSingle());
			case SINGLE_SHARED_LOCK -> new DefaultSyncLogPublisher(appenders.asSingleSharedLock());
			case LIST -> new FanoutSyncLogPublisher(appenders.asList());
		};
		publisherHolder[0] = publisher;

		publisher.start(config);
		try {
			publisher.log(TestEventBuilder.of().build(b -> b.message("event1")));
		}
		finally {
			publisher.close();
		}

		List<String> aMessages = outputA.events().stream().map(e -> e.getKey().message()).toList();
		List<String> bMessages = outputB.events().stream().map(e -> e.getKey().message()).toList();

		// A always only sees event1 - it drops its own reentrant call regardless of
		// mode, since that check is about A's own lock either way.
		assertEquals(List.of("event1"), aMessages);

		switch (mode) {
			case SINGLE_SHARED_LOCK ->
				// The shared composite lock rejects the reentrant call before it
				// reaches any appender, so B never sees event2 at all.
				assertEquals(List.of("event1"), bMessages);
			case SINGLE, LIST ->
				// B's lock is independent and not held, so the nested reentrant call
				// (which happens *during* A's write, i.e. before the outer loop moves
				// on to B) delivers event2 to B first, then the outer loop delivers
				// event1 to B as normal.
				assertEquals(List.of("event2", "event1"), bMessages);
		}

		if (reentryFlag == AppenderFlag.REENTRY_LOG) {
			String diagnostic = metaLogBytes.toString(StandardCharsets.UTF_8);
			assertTrue(diagnostic.contains("reentrant appender"),
					() -> "expected reentry diagnostic for mode " + mode + ", got: " + diagnostic);
		}
	}

}

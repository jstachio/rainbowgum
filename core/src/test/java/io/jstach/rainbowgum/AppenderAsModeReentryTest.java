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
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.junit.jupiter.api.parallel.Isolated;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import io.jstach.rainbowgum.AppenderAsModeFlagPermutationTest.AsMode;
import io.jstach.rainbowgum.LogAppender.AppenderFlag;
import io.jstach.rainbowgum.LogAppender.Appenders;
import io.jstach.rainbowgum.output.ListLogOutput;

/**
 * Every appender always keeps its own independent lock (there used to also be a
 * {@code SHARED_APPENDER_LOCK}/{@code asSingleSharedLock()} strategy that gave a
 * composite one lock shared by all its appenders - removed after this test caught it
 * behaving inconsistently with everything else: it rejected a reentrant call in its
 * entirety before it reached any appender, while every other path only dropped it for the
 * specific appender reentering its own lock). With only the independent-lock strategy
 * left, {@link Appenders#asSingle()} and {@link Appenders#asList()} (via
 * {@link FanoutSyncLogPublisher}, which loops the same way) now necessarily agree: a
 * reentrant call only gets dropped for the specific appender that's reentering its own
 * lock - every other appender still receives it, nested ahead of the event that triggered
 * the reentry in the first place. This test pins that (still genuinely surprising)
 * ordering down for both.
 */
/*
 * Mutates the shared static MetaLog.output field - see MetaLogTest's identical note for
 * why @Isolated/@Execution(SAME_THREAD) are needed under the "fast" profile's parallel
 * test execution.
 */
@Isolated
@Execution(ExecutionMode.SAME_THREAD)
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
	void testReentrantCallOnlyDropsForTheReenteringAppender(AsMode mode, AppenderFlag reentryFlag) {
		LogConfig config = LogConfig.builder().build();
		// naughty: logs a second event from within its own output write.
		var outputA = new ListLogOutput();
		// innocent bystander.
		var outputB = new ListLogOutput();

		LogPublisher[] publisherHolder = new LogPublisher[1];
		outputA.setConsumer((e, s) -> {
			if (e.message().equals("event1")) {
				publisherHolder[0].log(TestLogEventFactory.of().event("event2"));
			}
		});

		/*
		 * The reentry flag is set both on each appender's own builder and via
		 * Appenders.flags(...). An individual DirectLogAppender's *lock* (as opposed to
		 * its flags field, used only for immediateFlush/REUSE_BUFFER selection) is fixed
		 * once, from whatever flags were on its builder at construction
		 * (LockLogAppender.withFlags always reuses `this.lock` unchanged, no matter what
		 * flags are merged in later) - so only a REENTRY_DROP/REENTRY_LOG set on the
		 * *appender builder* actually matters for the reentry check; Appenders.flags(...)
		 * alone would do nothing for it. Setting both keeps this test robust regardless
		 * of which one turns out to matter.
		 */
		List<LogProvider<LogAppender>> providers = List.of(
				LogAppender.builder("a")
					.encoder(LogFormatter.builder().message().encoder().build())
					.output(outputA)
					.flag(reentryFlag)
					.build(),
				LogAppender.builder("b")
					.encoder(LogFormatter.builder().message().encoder().build())
					.output(outputB)
					.flag(reentryFlag)
					.build());
		var appenders = new Appenders("test-route", config, providers).flags(Set.of(reentryFlag));

		LogPublisher publisher = switch (mode) {
			case SINGLE -> new DefaultSyncLogPublisher(appenders.asSingle());
			case LIST -> new FanoutSyncLogPublisher(appenders.asList());
		};
		publisherHolder[0] = publisher;

		publisher.start(config);
		try {
			publisher.log(TestLogEventFactory.of().event("event1"));
		}
		finally {
			publisher.close();
		}

		List<String> aMessages = outputA.events().stream().map(e -> e.getKey().message()).toList();
		List<String> bMessages = outputB.events().stream().map(e -> e.getKey().message()).toList();

		// A only sees event1 - it drops its own reentrant call.
		assertEquals(List.of("event1"), aMessages);
		// B's lock is independent and not held, so the nested reentrant call (which
		// happens *during* A's write, i.e. before the outer loop moves on to B)
		// delivers event2 to B first, then the outer loop delivers event1 to B as
		// normal.
		assertEquals(List.of("event2", "event1"), bMessages);

		if (reentryFlag == AppenderFlag.REENTRY_LOG) {
			String diagnostic = metaLogBytes.toString(StandardCharsets.UTF_8);
			assertTrue(diagnostic.contains("reentrant appender"),
					() -> "expected reentry diagnostic for mode " + mode + ", got: " + diagnostic);
		}
	}

}

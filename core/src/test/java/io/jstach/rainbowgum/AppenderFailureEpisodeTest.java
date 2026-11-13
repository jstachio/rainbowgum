package io.jstach.rainbowgum;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.lang.System.Logger.Level;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.Set;

import org.jspecify.annotations.Nullable;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.junit.jupiter.api.parallel.Isolated;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import io.jstach.rainbowgum.LogAppender.AppenderType;
import io.jstach.rainbowgum.output.ListLogOutput;

/**
 * A failing output fails every append until something changes, so only the first failure
 * of an episode is alerted, every failure is counted, and the next success ends the
 * episode with one warning.
 */
/*
 * Alerts are echoed to MetaLog.output, which is swapped out here (see MetaLogTest).
 */
@Isolated
@Execution(ExecutionMode.SAME_THREAD)
class AppenderFailureEpisodeTest {

	@BeforeEach
	void before() {
		MetaLog.output = () -> null;
	}

	@AfterEach
	void after() {
		MetaLog.output = () -> System.err;
	}

	@ParameterizedTest
	@EnumSource(AppenderType.class)
	void onlyTheFirstFailureOfAnEpisodeIsAlerted(AppenderType type) {
		var config = LogConfig.builder().build();
		var output = new ListLogOutput();
		boolean[] broken = { true };
		output.setConsumer((e, s) -> {
			if (broken[0]) {
				throw new IllegalStateException("disk full");
			}
		});
		var encoder = LogFormatter.builder().message().encoder().build().provide("test", config);
		var appender = DirectLogAppender.of("test", output, encoder, type, Set.of(), config);
		for (int i = 0; i < 5; i++) {
			appender.append(event("failing " + i));
		}
		broken[0] = false;
		appender.append(event("works"));
		broken[0] = true;
		appender.append(event("failing again"));

		List<String> alerts = config.alerts().dump().stream().map(a -> a.level() + " " + a.message()).toList();
		assertEquals(List.of("ERROR appender 'test' failed to append event",
				"WARNING appender 'test' recovered; 4 more event(s) failed to append after the reported failure",
				"ERROR appender 'test' failed to append event"), alerts);
		long failed = config.metrics()
			.snapshot()
			.stream()
			.filter(m -> m.name().equals(LogMetrics.EVENTS_FAILED_METRIC))
			.mapToLong(LogMetrics.Metric::value)
			.sum();
		assertEquals(6, failed);
	}

	@ParameterizedTest
	@EnumSource(AppenderType.class)
	void droppedReentryDoesNotEndAFailureEpisode(AppenderType type) {
		var config = LogConfig.builder().build();
		var output = new ListLogOutput();
		var encoder = LogFormatter.builder().message().encoder().build().provide("test", config);
		var appender = DirectLogAppender.of("test", output, encoder, type,
				Set.of(LogAppender.AppenderFlag.REENTRY_DROP), config);
		output.setConsumer((e, s) -> {
			if (e.message().equals("outer")) {
				appender.append(event("nested"));
			}
			throw new IllegalStateException("still broken");
		});
		appender.append(event("first"));
		appender.append(event("outer"));
		assertEquals(List.of("ERROR appender 'test' failed to append event"),
				config.alerts().dump().stream().map(a -> a.level() + " " + a.message()).toList());
	}

	@ParameterizedTest
	@EnumSource(AppenderType.class)
	void emptyBatchDoesNotReportRecovery(AppenderType type) {
		var config = LogConfig.builder().build();
		var output = new ListLogOutput();
		output.setConsumer((e, s) -> {
			throw new IllegalStateException("broken");
		});
		var encoder = LogFormatter.builder().message().encoder().build().provide("test", config);
		var appender = DirectLogAppender.of("test", output, encoder, type, Set.of(), config);
		appender.append(event("failure"));
		appender.append(new LogEvent[0], 0);
		assertEquals(List.of("ERROR appender 'test' failed to append event"),
				config.alerts().dump().stream().map(a -> a.level() + " " + a.message()).toList());
	}

	@ParameterizedTest
	@EnumSource(AppenderType.class)
	void anErrorFromEventDataIsAFailedAppend(AppenderType type) {
		var config = LogConfig.builder().build();
		var output = new ListLogOutput();
		var encoder = LogFormatter.builder().message().throwable().encoder().build().provide("test", config);
		var appender = DirectLogAppender.of("test", output, encoder, type, Set.of(), config);
		var bad = event("bad", new IllegalStateException() {
			@Override
			public String getMessage() {
				throw new StackOverflowError();
			}
		});
		appender.append(bad);
		appender.append(new LogEvent[] { bad }, 1);
		appender.append(event("works"));
		assertEquals(List.of("works"), output.events().stream().map(e -> e.getKey().message()).toList());
		assertEquals(List.of("ERROR appender 'test' failed to append event",
				"WARNING appender 'test' recovered; 1 more event(s) failed to append after the reported failure"),
				config.alerts().dump().stream().map(a -> a.level() + " " + a.message()).toList());
	}

	@ParameterizedTest
	@EnumSource(AppenderType.class)
	void failureDiagnosticsDoNotFormatTheBrokenCauseAgain(AppenderType type) {
		var diagnostics = new ByteArrayOutputStream();
		MetaLog.output = () -> new PrintStream(diagnostics, true, StandardCharsets.UTF_8);
		var config = LogConfig.builder()
			.appenderLock(type == AppenderType.SYNCHRONIZED_THREAD_LOCAL_BUFFER
					? LogConfig.Globals.AppenderLock.SYNCHRONIZED : LogConfig.Globals.AppenderLock.REENTRANT)
			.build();
		var output = new ListLogOutput();
		var encoder = LogFormatter.builder().message().throwable().encoder().build().provide("test", config);
		var appender = DirectLogAppender.of("test", output, encoder, type, Set.of(), config);
		boolean[] broken = { true };
		var bad = event("bad", new IllegalStateException() {
			@Override
			public String getMessage() {
				if (broken[0]) {
					throw new AssertionError("from getMessage()", this);
				}
				return "no longer broken";
			}
		});
		try {
			appender.append(bad);
			appender.append(event("after single"));
			appender.append(new LogEvent[] { bad }, 1);
			appender.append(event("after batch"));
		}
		finally {
			// If the error escapes, JUnit formats it. A throwable that is still broken
			// then breaks JUnit's own reporting, and Surefire counts no test at all.
			broken[0] = false;
		}
		assertEquals(List.of("after single", "after batch"),
				output.events().stream().map(e -> e.getKey().message()).toList());
		assertEquals(List.of("ERROR appender 'test' failed to append event",
				"WARNING appender 'test' recovered; 0 more event(s) failed to append after the reported failure",
				"ERROR appender 'test' failed to append batch of 1 event(s)",
				"WARNING appender 'test' recovered; 0 more event(s) failed to append after the reported failure"),
				config.alerts().dump().stream().map(a -> a.level() + " " + a.message()).toList());
		assertEquals(
				"""
						[ERROR] - RAINBOW_GUM - %1$s - appender 'test' failed to append event java.lang.AssertionError [stack trace unavailable: java.lang.AssertionError]
						[WARN] - RAINBOW_GUM - %1$s - appender 'test' recovered; 0 more event(s) failed to append after the reported failure
						[ERROR] - RAINBOW_GUM - %1$s - appender 'test' failed to append batch of 1 event(s) java.lang.AssertionError [stack trace unavailable: java.lang.AssertionError]
						[WARN] - RAINBOW_GUM - %1$s - appender 'test' recovered; 0 more event(s) failed to append after the reported failure
						"""
					.formatted(appender.getClass().getSimpleName()),
				diagnostics.toString(StandardCharsets.UTF_8));
	}

	@ParameterizedTest
	@EnumSource(AppenderType.class)
	void anErrorThatLeavesTheJvmUnusableIsRethrown(AppenderType type) {
		var config = LogConfig.builder().build();
		var output = new ListLogOutput();
		output.setConsumer((e, s) -> {
			throw new OutOfMemoryError("test");
		});
		var encoder = LogFormatter.builder().message().encoder().build().provide("test", config);
		var appender = DirectLogAppender.of("test", output, encoder, type, Set.of(), config);
		assertThrows(OutOfMemoryError.class, () -> appender.append(event("event")));
		assertThrows(OutOfMemoryError.class, () -> appender.append(new LogEvent[] { event("event") }, 1));
		assertEquals(List.of(), config.alerts().dump());
	}

	@Test
	void concurrentRecoveryAccountsForEveryFailedEvent() throws Exception {
		var config = LogConfig.builder().build();
		var output = new ListLogOutput();
		output.setConsumer((e, s) -> {
			if (e.message().equals("fail")) {
				throw new IllegalStateException("broken");
			}
		});
		var encoder = LogFormatter.builder().message().encoder().build().provide("test", config);
		var appender = DirectLogAppender.of("test", output, encoder, AppenderType.LOCK_THREAD_LOCAL_BUFFER, Set.of(),
				config);
		try (var executor = java.util.concurrent.Executors.newFixedThreadPool(4)) {
			var tasks = new java.util.ArrayList<java.util.concurrent.Future<?>>();
			for (int producer = 0; producer < 4; producer++) {
				tasks.add(executor.submit(() -> {
					for (int i = 0; i < 30; i++) {
						appender.append(event("fail"));
						appender.append(event("success"));
					}
				}));
			}
			for (var task : tasks) {
				task.get();
			}
		}
		appender.append(event("success"));
		long accounted = config.alerts().dump().stream().filter(a -> a.level() == Level.ERROR).count();
		var pattern = java.util.regex.Pattern.compile(
				"appender 'test' recovered; ([0-9]+) more event\\(s\\) failed to append after the reported failure");
		for (var alert : config.alerts().dump()) {
			if (alert.level() == Level.WARNING) {
				var matcher = pattern.matcher(alert.message());
				org.junit.jupiter.api.Assertions.assertTrue(matcher.matches(), alert.message());
				var amount = matcher.group(1);
				if (amount == null) {
					throw new AssertionError("Recovery count is missing");
				}
				accounted += Long.parseLong(amount);
			}
		}
		assertEquals(120, accounted);
		assertEquals(120,
				config.metrics()
					.snapshot()
					.stream()
					.filter(m -> m.name().equals(LogMetrics.EVENTS_FAILED_METRIC))
					.mapToLong(LogMetrics.Metric::value)
					.sum());
	}

	private static LogEvent event(String message) {
		return event(message, null);
	}

	private static LogEvent event(String message, @Nullable Throwable throwable) {
		return LogEvent.of(Instant.EPOCH, "main", 1, Level.INFO, "test", message, KeyValues.of(), throwable);
	}

}

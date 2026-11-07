package io.jstach.rainbowgum;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.lang.System.Logger.Level;
import java.time.Instant;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.AfterEach;
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

	private static LogEvent event(String message) {
		return LogEvent.of(Instant.EPOCH, "main", 1, Level.INFO, "test", message, KeyValues.of(), null);
	}

}

package io.jstach.rainbowgum;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import io.jstach.rainbowgum.LogAppender.AppenderFlag;
import io.jstach.rainbowgum.LogAppender.AppenderType;
import io.jstach.rainbowgum.output.ListLogOutput;

/*
 * PARALLEL_ENCODE must produce exactly what sequential encoding produces, in order.
 * Uses ForkJoinPool threads, so it lives in this module rather than core.
 */
class ParallelEncodeTest {

	@ParameterizedTest
	@ValueSource(ints = { 1, 31, 32, 33, 1000 })
	void parallelOutputMatchesSequential(int count) {
		var events = new LogEvent[count];
		for (int i = 0; i < count; i++) {
			events[i] = LogEvent.of(java.time.Instant.ofEpochSecond(i), "main", 1, java.lang.System.Logger.Level.INFO,
					"test", "message " + i, KeyValues.of(Map.of("i", "" + i)), null);
		}
		assertEquals(encode(events, false), encode(events, true));
	}

	private static List<String> encode(LogEvent[] events, boolean parallel) {
		var config = LogConfig.builder().build();
		var output = new ListLogOutput();
		var builder = LogAppender.builder("test")
			.output(output)
			.formatter(LogFormatter.builder().level().text(" ").message().text(" ").keyValues().newline().build())
			.appenderType(AppenderType.REUSE_BUFFER);
		if (parallel) {
			builder.flag(AppenderFlag.PARALLEL_ENCODE);
		}
		var appender = builder.build().provide("test", config);
		appender.start(config);
		// twice, so the reused per slot buffers are exercised
		appender.append(events, events.length);
		appender.append(events, events.length);
		appender.close();
		return output.events().stream().map(e -> e.getValue()).toList();
	}

}

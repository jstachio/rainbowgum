package io.jstach.rainbowgum.benchmark.encode;

import java.lang.System.Logger;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.concurrent.TimeUnit;

import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Level;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.TearDown;
import org.openjdk.jmh.annotations.Warmup;

import io.jstach.rainbowgum.KeyValues;
import io.jstach.rainbowgum.LogAppender;
import io.jstach.rainbowgum.LogAppender.AppenderFlag;
import io.jstach.rainbowgum.LogAppender.AppenderType;
import io.jstach.rainbowgum.LogConfig;
import io.jstach.rainbowgum.LogEncoder;
import io.jstach.rainbowgum.LogEvent;
import io.jstach.rainbowgum.LogFormatter;
import io.jstach.rainbowgum.file.FileOutput;
import io.jstach.rainbowgum.json.encoder.EcsEncoder;

/**
 * One operation is one batch appended the way the async publisher's worker does it:
 * {@code appender.append(events, batchSize)} on a single thread, through a real
 * {@link FileOutput} (default 8KB {@code BufferedOutputStream}, one flush per batch). The
 * file is {@code /dev/null} so the buffered write path and its write syscalls are
 * measured but disk throughput is not. Events per second = score * batchSize.
 */
@State(Scope.Benchmark)
@BenchmarkMode(Mode.Throughput)
@OutputTimeUnit(TimeUnit.SECONDS)
@Warmup(iterations = 3, time = 2)
@Measurement(iterations = 5, time = 2)
@Fork(2)
public class ParallelEncodeBenchmark {

	/**
	 * Encoder: a typical text pattern, or ECS JSON.
	 */
	@Param({ "pattern", "json" })
	public String encoding;

	/**
	 * Events per batch.
	 */
	@Param({ "64", "1024" })
	public int batchSize;

	/**
	 * Whether {@link AppenderFlag#PARALLEL_ENCODE} is set.
	 */
	@Param({ "false", "true" })
	public boolean parallel;

	private LogAppender appender;

	private LogEvent[] events;

	/**
	 * Builds the appender and events.
	 */
	@Setup(Level.Trial)
	public void setup() {
		var config = LogConfig.builder().build();
		LogEncoder encoder = switch (encoding) {
			case "pattern" -> LogFormatter.builder()
				.timeStamp()
				.text(" [")
				.threadName()
				.text("] ")
				.level()
				.text(" ")
				.loggerName()
				.text(" - ")
				.message()
				.text(" ")
				.keyValues()
				.newline()
				.encoder()
				.build()
				.provide("bench", config);
			case "json" -> EcsEncoder.of(b -> {
			}).provide("bench", config);
			default -> throw new IllegalArgumentException(encoding);
		};
		var builder = LogAppender.builder("bench")
			.output(FileOutput.of(b -> b.fileName("/dev/null")))
			.encoder(encoder)
			.appenderType(AppenderType.REUSE_BUFFER);
		if (parallel) {
			builder.flag(AppenderFlag.PARALLEL_ENCODE);
		}
		appender = builder.build().provide("bench", config);
		appender.start(config);
		events = new LogEvent[batchSize];
		for (int i = 0; i < batchSize; i++) {
			var kvs = new LinkedHashMap<String, String>();
			kvs.put("requestId", "req-" + i);
			kvs.put("traceId", "4bf92f3577b34da6a3ce929d0e0e4736");
			kvs.put("userId", "user-" + (i % 97));
			events[i] = LogEvent.of(Instant.ofEpochSecond(1_700_000_000L, i), "http-nio-8080-exec-" + (i % 8), i % 8,
					Logger.Level.INFO, "com.example.orders.OrderController",
					"Handled request path=/api/orders/" + i + " status=200 durationMs=" + (i % 50), KeyValues.of(kvs),
					null);
		}
	}

	/**
	 * Appends one batch.
	 */
	@Benchmark
	public void appendBatch() {
		appender.append(events, batchSize);
	}

	/**
	 * Closes the appender.
	 */
	@TearDown(Level.Trial)
	public void tearDown() {
		appender.close();
	}

}

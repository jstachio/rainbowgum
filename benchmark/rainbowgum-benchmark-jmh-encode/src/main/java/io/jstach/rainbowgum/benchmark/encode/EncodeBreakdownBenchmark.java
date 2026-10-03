package io.jstach.rainbowgum.benchmark.encode;

import java.lang.System.Logger;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.concurrent.ForkJoinPool;
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
import io.jstach.rainbowgum.LogConfig;
import io.jstach.rainbowgum.LogEncoder;
import io.jstach.rainbowgum.LogEvent;
import io.jstach.rainbowgum.LogFormatter;
import io.jstach.rainbowgum.LogOutput;
import io.jstach.rainbowgum.file.FileOutput;
import io.jstach.rainbowgum.json.encoder.EcsEncoder;

/**
 * Splits a 1024 event batch into its parts, without an appender: sequential encoding
 * alone (no write), and parallel encoding in coarse chunks (one ForkJoin task per chunk)
 * followed by the single threaded write of the encoded batch. One operation is one batch.
 */
@State(Scope.Benchmark)
@BenchmarkMode(Mode.Throughput)
@OutputTimeUnit(TimeUnit.SECONDS)
@Warmup(iterations = 3, time = 2)
@Measurement(iterations = 5, time = 2)
@Fork(2)
public class EncodeBreakdownBenchmark {

	static final int BATCH = 1024;

	static final int CHUNK = 256;

	/**
	 * Encoder: a typical text pattern, or ECS JSON.
	 */
	@Param({ "pattern", "json" })
	public String encoding;

	private LogEncoder encoder;

	private LogOutput output;

	private LogEncoder.Buffer[] buffers;

	private LogEvent[] events;

	/**
	 * Builds the encoder, output, buffers, and events.
	 */
	@Setup(Level.Trial)
	public void setup() {
		var config = LogConfig.builder().build();
		encoder = switch (encoding) {
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
		output = FileOutput.of(b -> b.fileName("/dev/null")).provide("bench", config);
		output.start(config);
		buffers = new LogEncoder.Buffer[BATCH];
		events = new LogEvent[BATCH];
		for (int i = 0; i < BATCH; i++) {
			buffers[i] = encoder.buffer(output.bufferHints());
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
	 * Sequential encoding into per event buffers, nothing written.
	 */
	@Benchmark
	public void encodeOnly() {
		for (int i = 0; i < BATCH; i++) {
			var b = buffers[i];
			b.clear();
			encoder.encode(events[i], b);
		}
	}

	/**
	 * Parallel encoding in chunks of 256, nothing written.
	 */
	@Benchmark
	public void parallelEncodeOnly() {
		var tasks = new ArrayList<java.util.concurrent.ForkJoinTask<?>>(BATCH / CHUNK);
		for (int start = 0; start < BATCH; start += CHUNK) {
			int from = start;
			tasks.add(ForkJoinPool.commonPool().submit(() -> {
				for (int i = from; i < from + CHUNK; i++) {
					var b = buffers[i];
					b.clear();
					encoder.encode(events[i], b);
				}
			}));
		}
		for (var t : tasks) {
			t.join();
		}
	}

	/**
	 * Parallel encoding in chunks of 256, then a single threaded write and flush.
	 */
	@Benchmark
	public void parallelChunkedThenWrite() {
		var tasks = new ArrayList<java.util.concurrent.ForkJoinTask<?>>(BATCH / CHUNK);
		for (int start = 0; start < BATCH; start += CHUNK) {
			int from = start;
			tasks.add(ForkJoinPool.commonPool().submit(() -> {
				for (int i = from; i < from + CHUNK; i++) {
					var b = buffers[i];
					b.clear();
					encoder.encode(events[i], b);
				}
			}));
		}
		for (var t : tasks) {
			t.join();
		}
		output.write(events, buffers, BATCH);
		output.flush();
	}

	/**
	 * Closes the output.
	 */
	@TearDown(Level.Trial)
	public void tearDown() {
		output.close();
	}

}

package io.jstach.rainbowgum.rainbowgum;

import java.lang.System.Logger.Level;
import java.net.URI;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.SplittableRandom;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import io.jstach.rainbowgum.CodexAsyncPublisher;
import io.jstach.rainbowgum.KeyValues;
import io.jstach.rainbowgum.LogAppender.AppenderType;
import io.jstach.rainbowgum.LogConfig;
import io.jstach.rainbowgum.LogEncoder.BufferHints;
import io.jstach.rainbowgum.LogEvent;
import io.jstach.rainbowgum.LogFormatter;
import io.jstach.rainbowgum.LogMetrics;
import io.jstach.rainbowgum.LogOutput;
import io.jstach.rainbowgum.LogPublisher;
import io.jstach.rainbowgum.LogPublisher.PublisherFactory;
import io.jstach.rainbowgum.RainbowGum;
import io.jstach.rainbowgum.format.StandardEventFormatter;

/**
 * Standalone comparison through public publisher factories. Times complete delivery,
 * checks event and byte counts, and samples producer call latency. Run in several fresh
 * JVMs; this is a workload benchmark rather than a substitute for JMH microbenchmarks.
 */
public final class AsyncPublisherBenchmark {

	private AsyncPublisherBenchmark() {
	}

	enum Implementation {

		BLOCKING, CODEX;

		PublisherFactory factory(int capacity) {
			return this == CODEX
					? CodexAsyncPublisher.builder().bufferSize(capacity).shutdownTimeout(Duration.ofSeconds(30)).build()
					: PublisherFactory.ofAsync(capacity);
		}

	}

	enum Threads {

		PLATFORM, VIRTUAL;

		Thread start(Runnable task) {
			return this == VIRTUAL ? Thread.ofVirtual().start(task) : Thread.ofPlatform().start(task);
		}

	}

	enum Format {

		MESSAGE, TTLL;

		LogFormatter formatter() {
			return this == TTLL ? StandardEventFormatter.builder().build() : LogFormatter.builder().message().build();
		}

	}

	/**
	 * Runs the matrix and emits CSV to stdout.
	 * @param args total events per run, warmups, measured repetitions, capacity, and
	 * optional order seed. Defaults: 500000, 2, 5, 1024, 0.
	 * @throws Exception if delivery or validation fails.
	 */
	public static void main(String[] args) throws Exception {
		int events = args.length > 0 ? Integer.parseInt(args[0]) : 500000;
		int warmups = args.length > 1 ? Integer.parseInt(args[1]) : 2;
		int repetitions = args.length > 2 ? Integer.parseInt(args[2]) : 5;
		int capacity = args.length > 3 ? Integer.parseInt(args[3]) : 1024;
		int seed = args.length > 4 ? Integer.parseInt(args[4]) : 0;
		if (events < 16384 || warmups < 0 || repetitions < 1 || capacity < 1) {
			throw new IllegalArgumentException(
					"Require at least 16384 events, nonnegative warmups, positive repetitions and positive capacity.");
		}
		System.err.printf("Java=%s; VM=%s; processors=%d; events=%d; warmups=%d; repetitions=%d; capacity=%d%n",
				System.getProperty("java.version"), System.getProperty("java.vm.name"),
				Runtime.getRuntime().availableProcessors(), events, warmups, repetitions, capacity);
		System.out.println(
				"implementation,threads,format,producers,capacity,iteration,events,seconds,events_per_second,p50_ns,p99_ns,bytes,flushes");
		for (var threads : Threads.values()) {
			for (var format : Format.values()) {
				for (int producers : new int[] { 1, 4, 16 }) {
					for (int iteration = -warmups; iteration < repetitions; iteration++) {
						var order = (iteration + warmups + seed) % 2 == 0
								? List.of(Implementation.BLOCKING, Implementation.CODEX)
								: List.of(Implementation.CODEX, Implementation.BLOCKING);
						for (var implementation : order) {
							var result = run(implementation, threads, format, producers, capacity, events);
							if (iteration >= 0) {
								System.out.printf(Locale.ROOT, "%s,%s,%s,%d,%d,%d,%d,%.6f,%.0f,%d,%d,%d,%d%n",
										implementation, threads, format, producers, capacity, iteration, result.events,
										result.seconds, result.events / result.seconds, result.p50, result.p99,
										result.bytes, result.flushes);
							}
						}
					}
				}
			}
		}
	}

	private record Result(long events, double seconds, long p50, long p99, long bytes, long flushes) {
	}

	private static Result run(Implementation implementation, Threads threads, Format format, int producers,
			int capacity, int requestedEvents) throws Exception {
		int perProducer = requestedEvents / producers;
		long expected = (long) perProducer * producers;
		var output = new CountingOutput();
		var config = LogConfig.builder().build();
		var publisherRef = new AtomicReference<LogPublisher>();
		var factory = implementation.factory(capacity);
		var formatter = format.formatter();
		try (var gum = RainbowGum.builder(config).route(r -> r.publisher((name, c, appenders) -> {
			var publisher = factory.create(name, c, appenders);
			publisherRef.set(publisher);
			return publisher;
		}).appender("benchmark", a -> a.output(output).formatter(formatter).appenderType(AppenderType.REUSE_BUFFER)))
			.build()) {
			gum.start();
			var publisher = java.util.Objects.requireNonNull(publisherRef.get());
			if (implementation == Implementation.BLOCKING
					&& !publisher.getClass().getSimpleName().equals("BlockingQueueAsyncLogPublisher")) {
				throw new IllegalStateException("Unexpected baseline publisher: " + publisher.getClass());
			}
			var ready = new CountDownLatch(producers);
			var start = new CountDownLatch(1);
			var failure = new AtomicReference<Throwable>();
			var workers = new ArrayList<Thread>();
			long[][] samples = new long[producers][perProducer / 1024];
			long expectedBytes = 0;
			for (int p = 0; p < producers; p++) {
				int index = p;
				var event = LogEvent.of(Instant.EPOCH, "producer-" + p, p, Level.INFO, "benchmark.publisher",
						"Request completed successfully: method=GET path=/hello status=200", KeyValues.of(), null);
				var formatted = new StringBuilder();
				formatter.format(formatted, event);
				expectedBytes += (long) formatted.length() * perProducer;
				workers.add(threads.start(() -> {
					// One randomly positioned sample per complete block avoids aligning
					// the measurements with queue capacity or every producer's first
					// call.
					var random = new SplittableRandom(index);
					int sample = 0;
					int nextSample = random.nextInt(1024);
					ready.countDown();
					try {
						start.await();
						for (int i = 0; i < perProducer; i++) {
							if (sample < samples[index].length && i == nextSample) {
								long before = System.nanoTime();
								publisher.log(event);
								samples[index][sample++] = System.nanoTime() - before;
								nextSample = sample * 1024 + random.nextInt(1024);
							}
							else {
								publisher.log(event);
							}
						}
					}
					catch (Throwable e) {
						failure.compareAndSet(null, e);
					}
				}));
			}
			if (!ready.await(10, TimeUnit.SECONDS)) {
				throw new IllegalStateException("Producers did not become ready.");
			}
			long before = System.nanoTime();
			start.countDown();
			for (var worker : workers) {
				worker.join(60000);
				if (worker.isAlive()) {
					throw new IllegalStateException("Producer timed out.");
				}
			}
			publisher.close();
			double seconds = (System.nanoTime() - before) / 1e9;
			long queued = config.metrics()
				.snapshot()
				.stream()
				.filter(m -> m.name().equals(LogMetrics.EVENTS_QUEUED_METRIC))
				.mapToLong(LogMetrics.Metric::value)
				.sum();
			if (failure.get() != null || !output.closed || output.events != expected || output.bytes != expectedBytes
					|| queued != 0 || !config.alerts().dump().isEmpty()) {
				throw new IllegalStateException("Delivery failed: expected=" + expected + ", actual=" + output.events
						+ ", queued=" + queued + ", alerts=" + config.alerts().dump(), failure.get());
			}
			long[] sorted = Arrays.stream(samples).flatMapToLong(Arrays::stream).sorted().toArray();
			return new Result(expected, seconds, sorted[sorted.length / 2], sorted[(sorted.length - 1) * 99 / 100],
					output.bytes, output.flushes);
		}
	}

	private static final class CountingOutput implements LogOutput {

		long events;

		long bytes;

		long flushes;

		volatile boolean closed;

		@Override
		public URI uri() {
			return URI.create("counting:///");
		}

		@Override
		public OutputType type() {
			return OutputType.MEMORY;
		}

		@Override
		public BufferHints bufferHints() {
			return WriteMethod.BYTES;
		}

		@Override
		public void write(LogEvent event, byte[] bytes, int off, int len, ContentType contentType) {
			this.events++;
			this.bytes += len;
		}

		@Override
		public void flush() {
			flushes++;
		}

		@Override
		public void close() {
			closed = true;
		}

	}

}

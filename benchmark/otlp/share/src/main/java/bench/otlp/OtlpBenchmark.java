package bench.otlp;

import java.lang.management.GarbageCollectorMXBean;
import java.lang.management.ManagementFactory;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CountDownLatch;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.MDC;

/**
 * Logs a fixed number of events through SLF4J and measures two things:
 * <ul>
 * <li><b>producer</b>: how long the application threads spend in the logging calls,</li>
 * <li><b>delivered</b>: how long until the collector has accepted every record, read from
 * the collector's own {@code otelcol_receiver_accepted_log_records} metric.</li>
 * </ul>
 * Each framework's {@code Main} configures OTLP export to the collector and passes its
 * SLF4J logger here, so every framework runs the same call sites with the same message,
 * arguments, and MDC.
 * <p>
 * Settings are system properties: {@code bench.events} (measured events, default
 * 200000), {@code bench.warmup} (warmup events, default 50000), {@code bench.threads}
 * (logging threads, default 4), {@code bench.metrics} (collector metrics URL, default
 * {@code http://localhost:8888/metrics}), {@code bench.out} (CSV file to append a result
 * row to, optional).
 */
public final class OtlpBenchmark {

	private static final Pattern ACCEPTED = Pattern
		.compile("^otelcol_receiver_accepted_log_records(?:_total)?\\{[^}]*\\}\\s+([0-9.eE+]+)", Pattern.MULTILINE);

	private static final Duration STALL = Duration.ofSeconds(5);

	private final String framework;

	private final Logger log;

	private final int events;

	private final int warmup;

	private final int threads;

	private final URI metrics;

	private final HttpClient http = HttpClient.newHttpClient();

	private OtlpBenchmark(String framework, Logger log) {
		this.framework = framework;
		this.log = log;
		this.events = Integer.getInteger("bench.events", 200_000);
		this.warmup = Integer.getInteger("bench.warmup", 50_000);
		this.threads = Integer.getInteger("bench.threads", 4);
		this.metrics = URI.create(System.getProperty("bench.metrics", "http://localhost:8888/metrics"));
	}

	/**
	 * Runs the warmup and the measured phase and prints the result.
	 * @param framework name in the report.
	 * @param log logger configured to export to the collector.
	 * @param flush called after logging, to ask the framework to send what it buffered.
	 */
	public static void run(String framework, Logger log, Runnable flush) throws Exception {
		new OtlpBenchmark(framework, log).run(flush);
	}

	private void run(Runnable flush) throws Exception {
		var warm = phase(warmup, flush);
		System.err.printf(Locale.ROOT, "%s warmup: %s%n", framework, warm.summary());
		var result = phase(events, flush);
		System.err.printf(Locale.ROOT, "%s: %s%n", framework, result.summary());
		String out = System.getProperty("bench.out");
		if (out != null) {
			var path = java.nio.file.Path.of(out);
			boolean header = !java.nio.file.Files.exists(path);
			try (var w = java.nio.file.Files.newBufferedWriter(path, java.nio.file.StandardOpenOption.CREATE,
					java.nio.file.StandardOpenOption.APPEND)) {
				if (header) {
					w.write(Result.CSV_HEADER + "\n");
				}
				w.write(result.csv(framework, threads) + "\n");
			}
		}
	}

	private Result phase(int count, Runnable flush) throws Exception {
		long before = accepted();
		var gcBefore = gc();
		long cpuBefore = cpuNanos();
		long allocBefore = allocated();

		long start = System.nanoTime();
		var ready = new CountDownLatch(threads);
		var go = new CountDownLatch(1);
		List<Thread> workers = new ArrayList<>();
		int perThread = count / threads;
		for (int t = 0; t < threads; t++) {
			int id = t;
			workers.add(Thread.ofPlatform().name("bench-" + t).start(() -> {
				MDC.put("requestId", "req-" + id);
				MDC.put("user", "ada");
				ready.countDown();
				await(go);
				for (int i = 0; i < perThread; i++) {
					log.info("Order {} shipped to warehouse {}", i, id);
				}
				MDC.clear();
			}));
		}
		ready.await();
		start = System.nanoTime();
		go.countDown();
		for (var w : workers) {
			w.join();
		}
		long produced = System.nanoTime() - start;
		flush.run();

		long expected = (long) perThread * threads;
		long delivered = 0;
		long lastProgress = System.nanoTime();
		long deliveredAt = System.nanoTime();
		// CPU and allocation are sampled at the last delivery, so the wait for a delivery
		// that never comes (dropped records) is not counted.
		long cpu = cpuNanos() - cpuBefore;
		long alloc = allocated() - allocBefore;
		while (true) {
			long now = accepted() - before;
			if (now != delivered) {
				delivered = now;
				lastProgress = System.nanoTime();
				deliveredAt = lastProgress;
				cpu = cpuNanos() - cpuBefore;
				alloc = allocated() - allocBefore;
			}
			if (delivered >= expected || System.nanoTime() - lastProgress > STALL.toNanos()) {
				break;
			}
			Thread.sleep(20);
		}
		var gcAfter = gc();
		return new Result(expected, delivered, produced, deliveredAt - start, cpu, alloc, gcAfter[0] - gcBefore[0],
				gcAfter[1] - gcBefore[1]);
	}

	private static void await(CountDownLatch latch) {
		try {
			latch.await();
		}
		catch (InterruptedException e) {
			Thread.currentThread().interrupt();
		}
	}

	private long accepted() throws Exception {
		var body = http.send(HttpRequest.newBuilder(metrics).build(), HttpResponse.BodyHandlers.ofString()).body();
		Matcher m = ACCEPTED.matcher(body);
		double total = 0;
		while (m.find()) {
			total += Double.parseDouble(m.group(1));
		}
		return (long) total;
	}

	private static long cpuNanos() {
		return ((com.sun.management.OperatingSystemMXBean) ManagementFactory.getOperatingSystemMXBean())
			.getProcessCpuTime();
	}

	private static long allocated() {
		return ((com.sun.management.ThreadMXBean) ManagementFactory.getThreadMXBean()).getTotalThreadAllocatedBytes();
	}

	private static long[] gc() {
		long count = 0;
		long millis = 0;
		for (GarbageCollectorMXBean b : ManagementFactory.getGarbageCollectorMXBeans()) {
			count += Math.max(0, b.getCollectionCount());
			millis += Math.max(0, b.getCollectionTime());
		}
		return new long[] { count, millis };
	}

	record Result(long expected, long delivered, long producedNanos, long deliveredNanos, long cpuNanos,
			long allocatedBytes, long gcCount, long gcMillis) {

		static final String CSV_HEADER = "framework,threads,expected,delivered,produce_ms,produce_ops_s,deliver_ms,deliver_ops_s,cpu_ms,alloc_mb,gc_count,gc_ms";

		double produceOps() {
			return expected * 1e9 / producedNanos;
		}

		double deliverOps() {
			return delivered * 1e9 / deliveredNanos;
		}

		String summary() {
			return String.format(Locale.ROOT,
					"delivered %d/%d (%.1f%%), produce %.0f ms (%.0f ops/s), deliver %.0f ms (%.0f rec/s), cpu %.0f ms, alloc %.0f MB, gc %d (%d ms)",
					delivered, expected, 100.0 * delivered / expected, producedNanos / 1e6, produceOps(),
					deliveredNanos / 1e6, deliverOps(), cpuNanos / 1e6, allocatedBytes / 1048576.0, gcCount, gcMillis);
		}

		String csv(String framework, int threads) {
			return String.format(Locale.ROOT, "%s,%d,%d,%d,%.1f,%.0f,%.1f,%.0f,%.0f,%.1f,%d,%d", framework, threads,
					expected, delivered, producedNanos / 1e6, produceOps(), deliveredNanos / 1e6, deliverOps(),
					cpuNanos / 1e6, allocatedBytes / 1048576.0, gcCount, gcMillis);
		}

	}

}

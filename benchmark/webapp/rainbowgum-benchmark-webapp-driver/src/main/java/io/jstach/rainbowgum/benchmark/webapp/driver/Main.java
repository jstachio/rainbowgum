package io.jstach.rainbowgum.benchmark.webapp.driver;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse.BodyHandlers;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import org.eclipse.jdt.annotation.Nullable;

import io.jstach.rainbowgum.benchmark.webapp.driver.RssSampler.Stats;

/**
 * A small, dependency-free HTTP load generator: N virtual threads hammer a URL in a
 * closed loop for a fixed duration, latencies are collected into plain arrays and
 * summarized with simple percentiles (no HdrHistogram - this is meant to be a rough,
 * hand-run comparison, not a rigorous statistical tool). Optionally samples RSS of a
 * target pid via {@code /proc/<pid>/status} while the measurement runs.
 */
public class Main {

	private Main() {
	}

	/**
	 * Entry point.
	 * @param args see {@link Args} for supported flags.
	 */
	public static void main(String[] args) {
		Args a = Args.parse(args);

		System.out.println("target:      " + a.url());
		System.out.println("concurrency: " + a.concurrency());

		try (HttpClient client = HttpClient.newBuilder()
			.executor(Executors.newVirtualThreadPerTaskExecutor())
			.build()) {
			if (a.warmupSeconds() > 0) {
				System.out.println("warmup:      " + a.warmupSeconds() + "s (discarded)");
				run(client, a.url(), a.concurrency(), a.warmupSeconds());
			}

			System.out.println("measuring:   " + a.durationSeconds() + "s");
			@Nullable
			RssSampler sampler = a.pid() > 0 ? RssSampler.start(a.pid()) : null;
			long[] latenciesNanos = run(client, a.url(), a.concurrency(), a.durationSeconds());
			@Nullable
			Stats rss = sampler == null ? null : sampler.stop();

			Result result = Result.of(a.label(), a.durationSeconds(), latenciesNanos, rss);
			result.printTo(System.out);
			if (a.out() != null) {
				result.appendTo(a.out());
			}
		}
	}

	private static long[] run(HttpClient client, URI url, int concurrency, int seconds) {
		Instant deadline = Instant.now().plusSeconds(seconds);
		HttpRequest request = HttpRequest.newBuilder(url).GET().build();
		try (ExecutorService pool = Executors.newVirtualThreadPerTaskExecutor()) {
			List<Callable<long[]>> workers = new ArrayList<>(concurrency);
			for (int i = 0; i < concurrency; i++) {
				workers.add(() -> worker(client, request, deadline));
			}
			try {
				List<Future<long[]>> futures = pool.invokeAll(workers);
				List<long[]> parts = new ArrayList<>(futures.size());
				int total = 0;
				for (var f : futures) {
					long[] part = f.get();
					parts.add(part);
					total += part.length;
				}
				long[] merged = new long[total];
				int offset = 0;
				for (long[] part : parts) {
					System.arraycopy(part, 0, merged, offset, part.length);
					offset += part.length;
				}
				return merged;
			}
			catch (InterruptedException e) {
				Thread.currentThread().interrupt();
				throw new RuntimeException(e);
			}
			catch (java.util.concurrent.ExecutionException e) {
				throw new RuntimeException(e.getCause());
			}
		}
	}

	private static long[] worker(HttpClient client, HttpRequest request, Instant deadline) {
		long[] buffer = new long[4096];
		int count = 0;
		while (Instant.now().isBefore(deadline)) {
			long start = System.nanoTime();
			try {
				client.send(request, BodyHandlers.discarding());
			}
			catch (IOException e) {
				throw new UncheckedIOException(e);
			}
			catch (InterruptedException e) {
				Thread.currentThread().interrupt();
				break;
			}
			long elapsed = System.nanoTime() - start;
			if (count == buffer.length) {
				buffer = Arrays.copyOf(buffer, buffer.length * 2);
			}
			buffer[count++] = elapsed;
		}
		return Arrays.copyOf(buffer, count);
	}

	record Result(String label, int durationSeconds, long count, double throughputPerSecond, double p50Millis,
			double p90Millis, double p99Millis, double maxMillis, double meanMillis, @Nullable Stats rss) {

		static Result of(String label, int durationSeconds, long[] latenciesNanos, @Nullable Stats rss) {
			long[] sorted = latenciesNanos.clone();
			Arrays.sort(sorted);
			int n = sorted.length;
			double throughput = n / (double) durationSeconds;
			double sum = 0;
			for (long v : sorted) {
				sum += v;
			}
			double mean = n == 0 ? 0 : (sum / n) / 1_000_000d;
			return new Result(label, durationSeconds, n, throughput, percentile(sorted, 0.50), percentile(sorted, 0.90),
					percentile(sorted, 0.99), n == 0 ? 0 : sorted[n - 1] / 1_000_000d, mean, rss);
		}

		private static double percentile(long[] sorted, double p) {
			if (sorted.length == 0) {
				return 0;
			}
			int idx = (int) Math.min(sorted.length - 1, Math.floor(sorted.length * p));
			return sorted[idx] / 1_000_000d;
		}

		void printTo(java.io.PrintStream out) {
			out.printf(Locale.ROOT, "requests:    %d%n", count);
			out.printf(Locale.ROOT, "throughput:  %.1f req/s%n", throughputPerSecond);
			out.printf(Locale.ROOT, "latency ms:  p50=%.2f p90=%.2f p99=%.2f max=%.2f mean=%.2f%n", p50Millis,
					p90Millis, p99Millis, maxMillis, meanMillis);
			if (rss != null) {
				out.printf(Locale.ROOT, "rss MB:      min=%.1f max=%.1f avg=%.1f%n", rss.minMb(), rss.maxMb(),
						rss.avgMb());
			}
		}

		void appendTo(Path csv) {
			boolean writeHeader = !Files.exists(csv);
			StringBuilder sb = new StringBuilder();
			if (writeHeader) {
				sb.append(
						"label,requests,throughputPerSecond,p50Millis,p90Millis,p99Millis,maxMillis,meanMillis,rssMinMb,rssMaxMb,rssAvgMb\n");
			}
			sb.append(label).append(',');
			sb.append(count).append(',');
			sb.append(String.format(Locale.ROOT, "%.2f", throughputPerSecond)).append(',');
			sb.append(String.format(Locale.ROOT, "%.2f", p50Millis)).append(',');
			sb.append(String.format(Locale.ROOT, "%.2f", p90Millis)).append(',');
			sb.append(String.format(Locale.ROOT, "%.2f", p99Millis)).append(',');
			sb.append(String.format(Locale.ROOT, "%.2f", maxMillis)).append(',');
			sb.append(String.format(Locale.ROOT, "%.2f", meanMillis)).append(',');
			sb.append(rss == null ? "" : String.format(Locale.ROOT, "%.1f", rss.minMb())).append(',');
			sb.append(rss == null ? "" : String.format(Locale.ROOT, "%.1f", rss.maxMb())).append(',');
			sb.append(rss == null ? "" : String.format(Locale.ROOT, "%.1f", rss.avgMb())).append('\n');
			try {
				Files.writeString(csv, sb.toString(), StandardOpenOption.CREATE, StandardOpenOption.APPEND);
			}
			catch (IOException e) {
				throw new UncheckedIOException(e);
			}
		}
	}

}

package io.jstach.rainbowgum.benchmark.micro;

import java.io.IOException;
import java.io.PrintStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Locale;

import org.slf4j.Logger;

/**
 * Drives every {@link Scenario} x {@link ThreadingMode} combination for one framework and
 * reports results. Progress/results go to {@link System#err} (not {@code System.out}, so
 * they don't get mixed into whatever the framework under test is writing to the console).
 *
 * <p>
 * System properties (all optional): {@code bench.warmup} (default 1000, ops/worker),
 * {@code bench.measure} (default 5000, ops/worker), {@code bench.concurrency} (default
 * 16, workers for PLATFORM/VIRTUAL modes), {@code bench.out} (CSV file path to append
 * results to, in addition to the human-readable report on stderr).
 */
public final class BenchmarkRunner {

	private BenchmarkRunner() {
	}

	/**
	 * Runs the full scenario x threading-mode matrix for one framework.
	 * @param framework label included in every result row, e.g. "logback".
	 * @param log logger under test - already configured with the common TTLL layout and
	 * an INFO root level by the caller.
	 */
	public static void runAll(String framework, Logger log) {
		long warmup = Long.getLong("bench.warmup", 1_000);
		long measure = Long.getLong("bench.measure", 5_000);
		int concurrency = Integer.getInteger("bench.concurrency", 16);
		String out = System.getProperty("bench.out");

		PrintStream report = System.err;
		report.printf(Locale.ROOT, "framework=%s warmup=%d measure=%d concurrency=%d%n", framework, warmup, measure,
				concurrency);
		report.println("scenario,mode,concurrency,totalOps,elapsedMs,opsPerSecond");

		for (Scenario scenario : Scenario.values()) {
			for (ThreadingMode mode : ThreadingMode.values()) {
				BenchResult r = Runner.run(framework, log, scenario, mode, concurrency, warmup, measure);
				report.printf(Locale.ROOT, "%s,%s,%d,%d,%.1f,%.1f%n", r.scenario(), r.mode(), r.concurrency(),
						r.totalOps(), r.elapsedMillis(), r.opsPerSecond());
				if (out != null) {
					appendCsv(out, r);
				}
			}
		}
	}

	private static void appendCsv(String path, BenchResult r) {
		Path p = Path.of(path);
		boolean header = !Files.exists(p);
		StringBuilder sb = new StringBuilder();
		if (header) {
			sb.append("framework,scenario,mode,concurrency,totalOps,elapsedMs,opsPerSecond\n");
		}
		sb.append(r.framework()).append(',');
		sb.append(r.scenario()).append(',');
		sb.append(r.mode()).append(',');
		sb.append(r.concurrency()).append(',');
		sb.append(r.totalOps()).append(',');
		sb.append(String.format(Locale.ROOT, "%.1f", r.elapsedMillis())).append(',');
		sb.append(String.format(Locale.ROOT, "%.1f", r.opsPerSecond())).append('\n');
		try {
			Files.writeString(p, sb.toString(), StandardOpenOption.CREATE, StandardOpenOption.APPEND);
		}
		catch (IOException e) {
			throw new UncheckedIOException(e);
		}
	}

}

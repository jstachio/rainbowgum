package io.jstach.rainbowgum.benchmark.micro;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import org.slf4j.Logger;

/**
 * Runs a warmup phase (discarded) followed by a measured phase of a {@link Scenario}
 * under a given {@link ThreadingMode}.
 */
public final class Runner {

	private Runner() {
	}

	/**
	 * Runs warmup then measurement for one scenario/mode combination.
	 * @param framework label for the result (e.g. "logback").
	 * @param log logger under test.
	 * @param scenario call shape to run.
	 * @param mode threading model.
	 * @param concurrency worker count for {@link ThreadingMode#PLATFORM}/
	 * {@link ThreadingMode#VIRTUAL} (ignored for {@link ThreadingMode#SINGLE}, which is
	 * always exactly one worker).
	 * @param warmupOpsPerWorker iterations per worker during warmup (discarded).
	 * @param measureOpsPerWorker iterations per worker during measurement.
	 * @return result.
	 */
	public static BenchResult run(String framework, Logger log, Scenario scenario, ThreadingMode mode, int concurrency,
			long warmupOpsPerWorker, long measureOpsPerWorker) {
		int workers = mode == ThreadingMode.SINGLE ? 1 : concurrency;

		runWorkers(log, scenario, mode, workers, warmupOpsPerWorker);

		long start = System.nanoTime();
		long totalOps = runWorkers(log, scenario, mode, workers, measureOpsPerWorker);
		long elapsed = System.nanoTime() - start;

		return new BenchResult(framework, scenario, mode, workers, totalOps, elapsed);
	}

	private static long runWorkers(Logger log, Scenario scenario, ThreadingMode mode, int workers, long opsPerWorker) {
		Blackhole bh = new Blackhole();
		if (mode == ThreadingMode.SINGLE) {
			for (long i = 0; i < opsPerWorker; i++) {
				scenario.run(log, bh);
			}
			return opsPerWorker;
		}
		ExecutorService pool = mode == ThreadingMode.VIRTUAL ? Executors.newVirtualThreadPerTaskExecutor()
				: Executors.newFixedThreadPool(workers);
		try (pool) {
			List<Callable<Long>> tasks = new ArrayList<>(workers);
			for (int w = 0; w < workers; w++) {
				tasks.add(() -> {
					for (long i = 0; i < opsPerWorker; i++) {
						scenario.run(log, bh);
					}
					return opsPerWorker;
				});
			}
			long total = 0;
			for (Future<Long> f : pool.invokeAll(tasks)) {
				total += f.get();
			}
			return total;
		}
		catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			throw new RuntimeException(e);
		}
		catch (ExecutionException e) {
			throw new RuntimeException(e.getCause());
		}
	}

}

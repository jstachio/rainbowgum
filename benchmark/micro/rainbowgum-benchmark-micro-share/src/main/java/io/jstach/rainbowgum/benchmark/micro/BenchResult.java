package io.jstach.rainbowgum.benchmark.micro;

/**
 * One measured result: {@code concurrency} workers together performed {@code totalOps}
 * calls of {@code scenario} in {@code elapsedNanos} wall-clock time.
 */
public record BenchResult(String framework, Scenario scenario, ThreadingMode mode, int concurrency, long totalOps,
		long elapsedNanos) {

	/**
	 * Aggregate throughput across all concurrent workers.
	 * @return ops/second.
	 */
	public double opsPerSecond() {
		return totalOps / (elapsedNanos / 1_000_000_000.0);
	}

	/**
	 * Elapsed wall-clock time in milliseconds.
	 * @return milliseconds.
	 */
	public double elapsedMillis() {
		return elapsedNanos / 1_000_000.0;
	}

}

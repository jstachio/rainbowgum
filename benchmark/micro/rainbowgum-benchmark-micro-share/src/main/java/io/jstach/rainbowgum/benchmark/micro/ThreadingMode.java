package io.jstach.rainbowgum.benchmark.micro;

/**
 * How {@link Runner} drives a {@link Scenario}.
 */
public enum ThreadingMode {

	/**
	 * One caller thread, no concurrency at all - examines the pure pipeline cost without
	 * any lock contention or thread-scheduling effects mixed in.
	 */
	SINGLE,
	/**
	 * A fixed pool of platform threads.
	 */
	PLATFORM,
	/**
	 * One virtual thread per concurrent task.
	 */
	VIRTUAL

}

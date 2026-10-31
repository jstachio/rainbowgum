package io.jstach.rainbowgum.benchmark.freeze;

import java.util.concurrent.TimeUnit;

import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.Warmup;

import io.jstach.rainbowgum.LogEvent;

/**
 * The work freezing adds, measured on one thread without a publisher. {@link #freeze()}
 * is what an async route costs the logging thread. The format benchmarks add the work the
 * writing thread does for one or three appenders, and {@link #format()} is a synchronous
 * route, which never freezes.
 */
@State(Scope.Thread)
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@Warmup(iterations = 3, time = 2)
@Measurement(iterations = 5, time = 2)
@Fork(2)
public class FreezeBenchmark {

	/**
	 * Arguments logged.
	 */
	@Param
	public Events events;

	private final StringBuilder sb = new StringBuilder(256);

	/**
	 * Creates and freezes an event.
	 * @return frozen event.
	 */
	@Benchmark
	public LogEvent freeze() {
		return events.create().freeze();
	}

	/**
	 * Creates, freezes and formats an event once.
	 * @return formatted length.
	 */
	@Benchmark
	public int freezeAndFormat() {
		var e = events.create().freeze();
		sb.setLength(0);
		e.formattedMessage(sb);
		return sb.length();
	}

	/**
	 * Creates and freezes an event, then formats it three times as three appenders do.
	 * @return formatted length.
	 */
	@Benchmark
	public int freezeAndFormatThree() {
		var e = events.create().freeze();
		int length = 0;
		for (int i = 0; i < 3; i++) {
			sb.setLength(0);
			e.formattedMessage(sb);
			length += sb.length();
		}
		return length;
	}

	/**
	 * Creates and formats an event without freezing it.
	 * @return formatted length.
	 */
	@Benchmark
	public int format() {
		var e = events.create();
		sb.setLength(0);
		e.formattedMessage(sb);
		return sb.length();
	}

}

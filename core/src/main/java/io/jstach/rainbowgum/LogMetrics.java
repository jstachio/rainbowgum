package io.jstach.rainbowgum;

import java.lang.System.Logger.Level;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.LongAdder;

/**
 * Metrics are cheap, numeric counters about the logging system itself - for example how
 * many events an appender has dropped, or how often a reused encoder buffer had to shrink
 * itself back down. Unlike {@link LogAlerts}, which records discrete events (each with a
 * message/throwable, kept in a bounded ring buffer), metrics are just running totals: no
 * per-call allocation, no ring buffer entry, no listener dispatch.
 * <p>
 * An instance is available from every {@link LogConfig#metrics()}. Components that are
 * {@linkplain LogProvider provided} config, or that are
 * {@linkplain LogLifecycle#start( LogConfig) started} with config, should prefer
 * capturing {@code config.metrics()} over reaching for global state.
 * <p>
 * Deliberately a separate type from {@link LogAlerts} rather than more methods on it -
 * the two are meant to be independently replaceable (e.g. a future Micrometer-backed
 * {@code LogMetrics} without needing to also replace how alerts are recorded).
 *
 * @see LogConfig#metrics()
 */
public sealed interface LogMetrics permits DefaultLogMetrics {

	/**
	 * Counter name for the global count of log events dropped without ever being written
	 * anywhere - for example an appender dropping events on reentry. Incremented whenever
	 * a drop happens regardless of whether that particular drop is also logged/alerted,
	 * since counting and alerting/logging are separate concerns.
	 */
	static final String EVENTS_DROPPED_METRIC = "events.dropped";

	/**
	 * Counter name for the global count of times a reused encoder buffer had its backing
	 * storage shrunk back down after growing past its configured max size (see
	 * {@link LogEncoder.Buffer#isOversized()}). An occasional trim is normal and expected
	 * once in a while, but resizing <em>often</em> is a sign the configured max size (or
	 * the initial size) doesn't match the actual event sizes being logged - worth
	 * watching, not erroring on, hence {@link #warnCounter(String, long)} rather than
	 * {@link #errorCounter(String, long)}.
	 */
	static final String BUFFER_TRIMMED_METRIC = "buffer.trimmed";

	/**
	 * Increments a counter for something worth tracking as "this happens and it matters".
	 * @param name counter name, e.g. {@link #EVENTS_DROPPED_METRIC} or a logger name.
	 * @param increment amount to add, usually {@code 1}.
	 */
	public void errorCounter(String name, long increment);

	/**
	 * Like {@link #errorCounter(String, long)} but for something worth tracking yet less
	 * significant than an error - a trend worth watching rather than something that, by
	 * itself, indicates a problem. Kept as a separate counter namespace from
	 * {@link #errorCounter(String, long)}: the same {@code name} passed to both is two
	 * distinct counters, not one shared one.
	 * @param name counter name, e.g. {@link #BUFFER_TRIMMED_METRIC}.
	 * @param increment amount to add, usually {@code 1}.
	 */
	public void warnCounter(String name, long increment);

	/**
	 * A snapshot of every counter recorded via {@link #errorCounter(String, long)} and
	 * {@link #warnCounter(String, long)}. Counters are monotonically increasing for the
	 * life of the process, like a Prometheus/Micrometer counter - there is no reset
	 * method; a downstream metrics system computes rate of change rather than relying on
	 * the counter itself being reset.
	 * @return immutable snapshot.
	 */
	public List<Counter> counters();

	/**
	 * A single named counter's current value, as returned by {@link #counters()}.
	 *
	 * @param name counter name, as passed to a counter method like
	 * {@link #errorCounter(String, long)}.
	 * @param level how much this counter matters - not a log level routing decision, just
	 * a signal of significance, mirroring the counter method it came from (e.g.
	 * {@link Level#ERROR} for {@link #errorCounter(String, long)}, {@link Level#WARNING}
	 * for {@link #warnCounter(String, long)}).
	 * @param count current value.
	 */
	record Counter(String name, Level level, long count) {
	}

	/**
	 * Creates a new, empty metrics instance.
	 * @return metrics.
	 */
	public static LogMetrics of() {
		return new DefaultLogMetrics();
	}

}

final class DefaultLogMetrics implements LogMetrics {

	private final ConcurrentHashMap<String, LongAdder> errorCounters = new ConcurrentHashMap<>();

	private final ConcurrentHashMap<String, LongAdder> warnCounters = new ConcurrentHashMap<>();

	@Override
	public void errorCounter(String name, long increment) {
		errorCounters.computeIfAbsent(name, k -> new LongAdder()).add(increment);
	}

	@Override
	public void warnCounter(String name, long increment) {
		warnCounters.computeIfAbsent(name, k -> new LongAdder()).add(increment);
	}

	@Override
	public List<Counter> counters() {
		List<Counter> list = new ArrayList<>(errorCounters.size() + warnCounters.size());
		for (var e : errorCounters.entrySet()) {
			list.add(new Counter(e.getKey(), Level.ERROR, e.getValue().sum()));
		}
		for (var e : warnCounters.entrySet()) {
			list.add(new Counter(e.getKey(), Level.WARNING, e.getValue().sum()));
		}
		return List.copyOf(list);
	}

}

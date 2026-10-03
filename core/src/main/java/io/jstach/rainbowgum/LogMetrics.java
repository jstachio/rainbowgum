package io.jstach.rainbowgum;

import java.lang.System.Logger.Level;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.LongAdder;

import io.jstach.rainbowgum.annotation.CaseChanging;

/**
 * Numeric metrics about the logging system itself. Counters track running totals such as
 * dropped events; gauges track values that can rise and fall, such as queued events.
 * Unlike {@link LogAlerts}, updating a metric does not create an alert or notify
 * listeners. Retain a {@link Gauge} for frequent updates without a name lookup.
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
	 * Gauge name for the aggregate number of events waiting in blocking async publisher
	 * queues. Events removed for processing are no longer counted, even while their
	 * output is being written. Concurrent snapshots can briefly lead or lag queue
	 * activity.
	 */
	static final String EVENTS_QUEUED_METRIC = "events.queued";

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
	 * Counter name for the global count of log events an appender failed to write - the
	 * encoder or output threw while appending, so the event was caught, alerted (see
	 * {@link LogAlerts#error(Class, String, Throwable)}), and lost rather than retried.
	 * Kept separate from {@link #EVENTS_DROPPED_METRIC}: a drop is a deliberate skip (the
	 * appender chose not to write), while this is an unexpected failure partway through
	 * actually trying to - different root causes worth distinguishing when triaging.
	 */
	static final String EVENTS_FAILED_METRIC = "events.failed";

	/**
	 * Counter name for the global count of failed appender output reopen attempts. Each
	 * failed attempt increments this counter in addition to recording an alert,
	 * regardless of the appender or output type.
	 */
	static final String REOPEN_FAIL_METRIC = "reopen.fail";

	/**
	 * Counter name for the global count of appender output reopen attempts, whether
	 * successful or failed. Subtract {@link #REOPEN_FAIL_METRIC} to obtain the number of
	 * successful attempts.
	 */
	static final String REOPEN_METRIC = "reopen";

	/**
	 * Counter name for failed automatic file rotation attempts, including closing the
	 * active output, rotating archives, and opening the replacement output. Recovery
	 * attempts that fail also increment this counter. External rotation followed by an
	 * explicit reopen is counted by {@link #REOPEN_FAIL_METRIC} instead. Events lost
	 * because rotation failed are also counted by {@link #EVENTS_FAILED_METRIC}.
	 */
	static final String ROLL_FAIL_METRIC = "roll.fail";

	/**
	 * Counter name for the running count of distinct logger names registered via
	 * {@link LogConfig.LoggerRegistry#registerLoggerName(LoggerAPI, String)}. Not itself
	 * a problem, hence {@link #infoCounter(String, long)} rather than
	 * {@link #warnCounter(String, long)}, but worth watching: a count that keeps climbing
	 * without bound is a sign something is using per-request/per-entity data (e.g. a
	 * request id) as a logger name instead of a fixed, bounded set of names.
	 */
	static final String LOGGER_NAMES_METRIC = "logger.names";

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
	 * Like {@link #errorCounter(String, long)} but for a plain running total, not
	 * inherently a problem or even a trend worth watching. Kept as a separate counter
	 * namespace from
	 * {@link #errorCounter(String, long)}/{@link #warnCounter(String, long)}: the same
	 * {@code name} passed to more than one of these is a distinct counter per method, not
	 * one shared one.
	 * @param name counter name, e.g. {@link #LOGGER_NAMES_METRIC}.
	 * @param increment amount to add, usually {@code 1}.
	 */
	public void infoCounter(String name, long increment);

	/**
	 * Creates a handle for updating a named value directly. For INFO, WARNING, and ERROR,
	 * each call returns a new handle sharing the value for the same name and level;
	 * retain the handle for frequent updates without repeated name lookups. The value is
	 * initially zero and appears in {@link #snapshot()} as soon as the handle is created.
	 * Counter methods for the same name and level share this value too. Levels below INFO
	 * and OFF return a shared no-op handle and do not register a metric.
	 * @param name metric name.
	 * @param level significance of the metric.
	 * @return a handle to the shared value, or a no-op handle.
	 */
	public Gauge gauge(String name, Level level);

	/**
	 * A retained handle for incrementing or decrementing a metric. Updates are thread
	 * safe and do not perform a name lookup. Read the value through {@link #snapshot()}.
	 */
	sealed interface Gauge permits DefaultGauge, NoopGauge {

		/**
		 * Returns a shared gauge that ignores all updates. Use it until a metric is
		 * available, or when metric collection is disabled.
		 * @return no-op gauge.
		 */
		public static Gauge noop() {
			return NoopGauge.NOOP;
		}

		/**
		 * Increments the value by one.
		 */
		public void increment();

		/**
		 * Adds the supplied amount.
		 * @param increment amount to add.
		 */
		public void increment(long increment);

		/**
		 * Decrements the value by one.
		 */
		public void decrement();

		/**
		 * Subtracts the supplied amount.
		 * @param decrement amount to subtract.
		 */
		public void decrement(long decrement);

	}

	/**
	 * A snapshot of every metric recorded via {@link #errorCounter(String, long)},
	 * {@link #warnCounter(String, long)}, {@link #infoCounter(String, long)}, and
	 * {@link #gauge(String, Level)}. Counter values increase over time; gauge values can
	 * also decrease. Snapshots are approximate while updates are concurrent.
	 * @return immutable snapshot.
	 */
	public List<Metric> snapshot();

	/**
	 * A single named metric's current value, as returned by {@link #snapshot()}.
	 *
	 * @param name metric name, as passed to a counter method or
	 * {@link #gauge(String, Level)}.
	 * @param level significance of the metric. Counter methods use {@link Level#ERROR},
	 * {@link Level#WARNING}, or {@link Level#INFO}; gauges use the level supplied to
	 * {@link #gauge(String, Level)}.
	 * @param value current value, which can decrease for a gauge.
	 */
	record Metric(String name, Level level, long value) {
	}

	/**
	 * The fixed, well known set of metrics RainbowGum itself records, as opposed to the
	 * open ended, per logger name counters {@link LogAlerts#alert(LogEvent)} also drives
	 * into the counter matching the alert's level. Enumerable on purpose: consumers that
	 * want to bind every well known counter to something else (a Micrometer counter or
	 * gauge per constant, for example) can loop over {@link #values()} instead of hand
	 * listing each {@code String}/{@link Level} pair themselves.
	 */
	@CaseChanging
	enum StandardMetric {

		/**
		 * See {@link #EVENTS_DROPPED_METRIC}.
		 */
		EVENTS_DROPPED(EVENTS_DROPPED_METRIC, Level.ERROR),
		/**
		 * See {@link #EVENTS_QUEUED_METRIC}.
		 */
		EVENTS_QUEUED(EVENTS_QUEUED_METRIC, Level.INFO, true),
		/**
		 * See {@link #BUFFER_TRIMMED_METRIC}.
		 */
		BUFFER_TRIMMED(BUFFER_TRIMMED_METRIC, Level.WARNING),
		/**
		 * See {@link #EVENTS_FAILED_METRIC}.
		 */
		EVENTS_FAILED(EVENTS_FAILED_METRIC, Level.ERROR),
		/**
		 * See {@link #LOGGER_NAMES_METRIC}.
		 */
		LOGGER_NAMES(LOGGER_NAMES_METRIC, Level.INFO),
		/**
		 * See {@link #REOPEN_FAIL_METRIC}.
		 */
		REOPEN_FAIL(REOPEN_FAIL_METRIC, Level.ERROR),
		/**
		 * See {@link #REOPEN_METRIC}.
		 */
		REOPEN(REOPEN_METRIC, Level.INFO),
		/**
		 * See {@link #ROLL_FAIL_METRIC}.
		 */
		ROLL_FAIL(ROLL_FAIL_METRIC, Level.ERROR);

		private final String metricName;

		private final Level level;

		private final boolean gauge;

		StandardMetric(String metricName, Level level) {
			this(metricName, level, false);
		}

		StandardMetric(String metricName, Level level, boolean gauge) {
			this.metricName = metricName;
			this.level = level;
			this.gauge = gauge;
		}

		/**
		 * The metric name, as passed to a counter method or {@link #gauge(String, Level)}
		 * and matched against {@link Metric#name()}.
		 * @return metric name.
		 */
		public String metricName() {
			return metricName;
		}

		/**
		 * The significance of this metric, also exposed through {@link Metric#level()} in
		 * {@link #snapshot()}.
		 * @return level.
		 */
		public Level level() {
			return level;
		}

		/**
		 * Whether this metric can decrease and should be bound as a gauge instead of an
		 * increasing counter.
		 * @return true for a gauge.
		 */
		public boolean isGauge() {
			return gauge;
		}

	}

}

final class DefaultLogMetrics implements LogMetrics {

	private final ConcurrentHashMap<String, LongAdder> errorCounters = new ConcurrentHashMap<>();

	private final ConcurrentHashMap<String, LongAdder> warnCounters = new ConcurrentHashMap<>();

	private final ConcurrentHashMap<String, LongAdder> infoCounters = new ConcurrentHashMap<>();

	@Override
	public Gauge gauge(String name, Level level) {
		return switch (level) {
			case ERROR -> new DefaultGauge(errorCounters.computeIfAbsent(name, k -> new LongAdder()));
			case WARNING -> new DefaultGauge(warnCounters.computeIfAbsent(name, k -> new LongAdder()));
			case INFO -> new DefaultGauge(infoCounters.computeIfAbsent(name, k -> new LongAdder()));
			case ALL, TRACE, DEBUG, OFF -> NoopGauge.NOOP;
		};
	}

	@Override
	public void errorCounter(String name, long increment) {
		errorCounters.computeIfAbsent(name, k -> new LongAdder()).add(increment);
	}

	@Override
	public void warnCounter(String name, long increment) {
		warnCounters.computeIfAbsent(name, k -> new LongAdder()).add(increment);
	}

	@Override
	public void infoCounter(String name, long increment) {
		infoCounters.computeIfAbsent(name, k -> new LongAdder()).add(increment);
	}

	@Override
	public List<Metric> snapshot() {
		List<Metric> list = new ArrayList<>(errorCounters.size() + warnCounters.size() + infoCounters.size());
		for (var e : errorCounters.entrySet()) {
			list.add(new Metric(e.getKey(), Level.ERROR, e.getValue().sum()));
		}
		for (var e : warnCounters.entrySet()) {
			list.add(new Metric(e.getKey(), Level.WARNING, e.getValue().sum()));
		}
		for (var e : infoCounters.entrySet()) {
			list.add(new Metric(e.getKey(), Level.INFO, e.getValue().sum()));
		}
		return List.copyOf(list);
	}

}

final class DefaultGauge implements LogMetrics.Gauge {

	private final LongAdder value;

	DefaultGauge(LongAdder value) {
		this.value = value;
	}

	@Override
	public void increment() {
		value.increment();
	}

	@Override
	public void increment(long increment) {
		value.add(increment);
	}

	@Override
	public void decrement() {
		value.decrement();
	}

	@Override
	public void decrement(long decrement) {
		value.add(-decrement);
	}

}

enum NoopGauge implements LogMetrics.Gauge {

	NOOP;

	@Override
	public void increment() {
	}

	@Override
	public void increment(long increment) {
	}

	@Override
	public void decrement() {
	}

	@Override
	public void decrement(long decrement) {
	}

}

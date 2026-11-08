package io.jstach.rainbowgum.filter;

import java.lang.System.Logger.Level;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicReference;

import org.jspecify.annotations.Nullable;

import io.jstach.rainbowgum.LevelResolver;
import io.jstach.rainbowgum.LogEvent;
import io.jstach.rainbowgum.LogMetrics;
import io.jstach.rainbowgum.LogProperties;
import io.jstach.rainbowgum.LogRouter.Router.Filter;
import io.jstach.rainbowgum.annotation.LogConfigurable;

/*
 * A generic cell rate algorithm: the bucket records the last accepted timestamp and
 * the outstanding interval debt. Events are dropped when admitting them would exceed
 * maxBurst intervals of debt. Time comes from event timestamps so no clock is read per
 * event. Timestamps are wall clock time, so if the clock steps back the bucket would
 * look full until the clock caught up. A bucket further ahead than it can get in
 * normal use is reset instead.
 */
final class ThrottleFilter implements Filter {

	static final int DEFAULT_RATE = 10;

	static final int DEFAULT_MAX_BURST = 100;

	/**
	 * {@link java.lang.System.Logger.Level#WARNING warning}, so errors are always kept
	 */
	static final Level DEFAULT_LEVEL = Level.WARNING;

	private static final long NANOS_PER_SECOND = 1_000_000_000L;

	private final long resetNanos;

	private final long intervalNanos;

	private final long burstNanos;

	private final int severity;

	private final LogMetrics.Gauge dropped;

	private final AtomicReference<@Nullable Bucket> bucket = new AtomicReference<>();

	private record Bucket(Instant timestamp, long debtNanos) {
	}

	private ThrottleFilter(long intervalNanos, long burstNanos, Level level, LogMetrics.Gauge dropped) {
		this.intervalNanos = intervalNanos;
		this.burstNanos = burstNanos;
		/*
		 * A bucket is never more than burstNanos ahead of the events filling it. Events
		 * from other threads can arrive slightly out of timestamp order, so allow a
		 * second of that before deciding the clock went back.
		 */
		this.resetNanos = burstNanos + Math.max(intervalNanos, NANOS_PER_SECOND);
		this.severity = level.getSeverity();
		this.dropped = dropped;
	}

	/**
	 * Limits how many events a route keeps per second, like a token bucket. A burst of up
	 * to <code>maxBurst</code> events is kept, after which events are kept at
	 * <code>rate</code> per second until the burst is earned back. Events more severe
	 * than <code>level</code> are always kept. Time is measured with event timestamps,
	 * with a minimum interval of one nanosecond.
	 * @param name route name.
	 * @param filter name the filter is selected with.
	 * @param rate events kept per second once a burst is used up.
	 * @param maxBurst events kept at once before the rate applies.
	 * @param level the most severe level that is throttled.
	 * @param metrics where to count dropped events as
	 * {@link io.jstach.rainbowgum.LogMetrics#EVENTS_THROTTLED_METRIC}.
	 * @return filter.
	 */
	@LogConfigurable(name = "ThrottleFilterBuilder", prefix = LogProperties.FILTER_PREFIX)
	static Filter of(@LogConfigurable.KeyParameter String name, @LogConfigurable.KeyParameter String filter,
			@LogConfigurable.DefaultParameter("DEFAULT_RATE") Integer rate,
			@LogConfigurable.DefaultParameter("DEFAULT_MAX_BURST") Integer maxBurst,
			@LogConfigurable.DefaultParameter("DEFAULT_LEVEL") @LogConfigurable.ConvertParameter("parseLevel") Level level,
			@LogConfigurable.PassThroughParameter @Nullable LogMetrics metrics) {
		if (rate < 1) {
			throw new IllegalArgumentException("rate must be positive.");
		}
		if (maxBurst < 1) {
			throw new IllegalArgumentException("maxBurst must be positive.");
		}
		long intervalNanos = Math.max(1, NANOS_PER_SECOND / rate);
		return new ThrottleFilter(intervalNanos, intervalNanos * maxBurst, level, metrics == null
				? LogMetrics.Gauge.noop() : metrics.gauge(LogMetrics.EVENTS_THROTTLED_METRIC, Level.WARNING));
	}

	static Level parseLevel(String value) {
		return LevelResolver.parseLevel(value);
	}

	@Override
	public @Nullable LogEvent filterOrNull(LogEvent event) {
		if (event.level().getSeverity() > severity) {
			return event;
		}
		Instant now = event.timestamp();
		while (true) {
			var current = bucket.get();
			long debt = 0;
			if (current != null) {
				long elapsed = elapsedNanos(current.timestamp(), now);
				if (elapsed >= 0) {
					debt = Math.max(0, current.debtNanos() - elapsed);
				}
				else if (elapsed >= -(resetNanos - current.debtNanos())) {
					// Small backwards steps consume burst allowance. A larger step
					// resets the bucket atomically with admitting this event.
					debt = current.debtNanos() - elapsed;
				}
			}
			if (debt > burstNanos - intervalNanos) {
				dropped.increment();
				return null;
			}
			if (bucket.compareAndSet(current, new Bucket(now, debt + intervalNanos))) {
				return event;
			}
		}
	}

	/*
	 * Compare event timestamps without converting absolute epoch time to a long of
	 * nanoseconds. Saturating differences beyond 292 years is sufficient: every supported
	 * burst is smaller than that, and no arithmetic wraps at Instant's edges.
	 */
	@SuppressWarnings("JavaInstantGetSecondsGetNano") // Combine whole seconds and their
														// subsecond adjustments.
	private static long elapsedNanos(Instant previous, Instant now) {
		long seconds = now.getEpochSecond() - previous.getEpochSecond();
		if (seconds > Long.MAX_VALUE / NANOS_PER_SECOND) {
			return Long.MAX_VALUE;
		}
		if (seconds < Long.MIN_VALUE / NANOS_PER_SECOND) {
			return Long.MIN_VALUE;
		}
		try {
			return Math.addExact(seconds * NANOS_PER_SECOND, (long) now.getNano() - previous.getNano());
		}
		catch (ArithmeticException e) {
			return seconds < 0 ? Long.MIN_VALUE : Long.MAX_VALUE;
		}
	}

}

package io.jstach.rainbowgum.filter;

import java.lang.System.Logger.Level;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicLong;

import org.jspecify.annotations.Nullable;

import io.jstach.rainbowgum.LevelResolver;
import io.jstach.rainbowgum.LogEvent;
import io.jstach.rainbowgum.LogMetrics;
import io.jstach.rainbowgum.LogProperties;
import io.jstach.rainbowgum.LogRouter.Router.Filter;
import io.jstach.rainbowgum.annotation.LogConfigurable;

/*
 * A generic cell rate algorithm: allowedAt is when the bucket is empty again, and an
 * event is dropped if keeping it would put that more than maxBurst intervals ahead of the
 * event. Time comes from event timestamps so no clock is read per event.
 */
final class ThrottleFilter implements Filter {

	static final int DEFAULT_RATE = 10;

	static final int DEFAULT_MAX_BURST = 100;

	/**
	 * {@link java.lang.System.Logger.Level#WARNING warning}, so errors are always kept
	 */
	static final Level DEFAULT_LEVEL = Level.WARNING;

	private static final long NANOS_PER_SECOND = 1_000_000_000L;

	private final long intervalNanos;

	private final long burstNanos;

	private final int severity;

	private final LogMetrics.Gauge dropped;

	private final AtomicLong allowedAt = new AtomicLong(Long.MIN_VALUE);

	private ThrottleFilter(long intervalNanos, long burstNanos, Level level, LogMetrics.Gauge dropped) {
		this.intervalNanos = intervalNanos;
		this.burstNanos = burstNanos;
		this.severity = level.getSeverity();
		this.dropped = dropped;
	}

	/**
	 * Limits how many events a route keeps per second, like a token bucket. A burst of up
	 * to <code>maxBurst</code> events is kept, after which events are kept at
	 * <code>rate</code> per second until the burst is earned back. Events more severe
	 * than <code>level</code> are always kept. Time is measured with event timestamps.
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
		long intervalNanos = NANOS_PER_SECOND / rate;
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
		long now = nanos(event.timestamp());
		while (true) {
			long current = allowedAt.get();
			long next = Math.max(current, now) + intervalNanos;
			if (next - now > burstNanos) {
				dropped.increment();
				return null;
			}
			if (allowedAt.compareAndSet(current, next)) {
				return event;
			}
		}
	}

	private static long nanos(Instant timestamp) {
		return timestamp.getEpochSecond() * NANOS_PER_SECOND + timestamp.getNano();
	}

}

package io.jstach.rainbowgum.spring.boot4.actuator;

import java.lang.System.Logger.Level;

import io.jstach.rainbowgum.LogMetrics;
import io.jstach.rainbowgum.RainbowGum;
import io.micrometer.core.instrument.FunctionCounter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.binder.MeterBinder;

/**
 * Bridges RainbowGum's {@link LogMetrics} counters to Micrometer as
 * {@link FunctionCounter}s - a pull based counter backed by a supplier, matching how
 * RainbowGum itself already owns and accumulates these values (see
 * {@link LogMetrics#errorCounter(String, long)}/{@link LogMetrics#warnCounter(String, long)})
 * rather than a push style {@link io.micrometer.core.instrument.Counter} that expects
 * Micrometer itself to own the increments.
 * <p>
 * Only binds the small, fixed set of well known counter names -
 * {@link LogMetrics#EVENTS_DROPPED_METRIC} and {@link LogMetrics#BUFFER_TRIMMED_METRIC} -
 * <strong>not</strong> the per logger name counters that {@link LogMetrics} also
 * accumulates as a side effect of {@code LogAlerts#error(LogEvent)}. Logger names are
 * effectively unbounded (arbitrary class/category names, chosen by application code), so
 * turning each one into its own Micrometer meter would be an unbounded cardinality source
 * - exactly what Micrometer's own naming guidance warns against. Reporting that per
 * logger detail is left to a future, explicitly opt in mechanism rather than bound
 * automatically here.
 */
final class RainbowGumMeterBinder implements MeterBinder {

	static final String METRIC_PREFIX = "rainbowgum.";

	RainbowGumMeterBinder() {
	}

	@Override
	public void bindTo(MeterRegistry registry) {
		var gum = RainbowGum.getOrNull();
		if (gum == null) {
			return;
		}
		var metrics = gum.config().metrics();
		bind(registry, metrics, LogMetrics.EVENTS_DROPPED_METRIC, Level.ERROR);
		bind(registry, metrics, LogMetrics.BUFFER_TRIMMED_METRIC, Level.WARNING);
	}

	private static void bind(MeterRegistry registry, LogMetrics metrics, String name, Level level) {
		FunctionCounter.builder(METRIC_PREFIX + name, metrics, m -> currentValue(m, name, level))
			.tag("level", level.toString())
			.register(registry);
	}

	private static long currentValue(LogMetrics metrics, String name, Level level) {
		for (var counter : metrics.counters()) {
			if (counter.name().equals(name) && counter.level() == level) {
				return counter.count();
			}
		}
		return 0;
	}

}

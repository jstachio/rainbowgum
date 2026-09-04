package io.jstach.rainbowgum.spring.boot4.actuator;

import io.jstach.rainbowgum.LogMetrics;
import io.jstach.rainbowgum.LogMetrics.StandardMetric;
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
 * Binds every {@link StandardMetric} by looping over {@link StandardMetric#values()},
 * <strong>not</strong> the per logger name counters that {@link LogMetrics} also
 * accumulates as a side effect of {@code LogAlerts#error(LogEvent)}. Logger names are
 * effectively unbounded (arbitrary class/category names, chosen by application code), so
 * turning each one into its own Micrometer meter would be an unbounded cardinality source
 * - exactly what Micrometer's own naming guidance warns against. Reporting that per
 * logger detail is left to a future, explicitly opt in mechanism rather than bound
 * automatically here. Looping over {@link StandardMetric#values()} rather than binding
 * each constant by hand also means a future new {@link StandardMetric} constant is bound
 * here automatically, with no change needed in this class.
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
		for (var metric : StandardMetric.values()) {
			bind(registry, metrics, metric);
		}
	}

	private static void bind(MeterRegistry registry, LogMetrics metrics, StandardMetric metric) {
		FunctionCounter.builder(METRIC_PREFIX + metric.metricName(), metrics, m -> currentValue(m, metric))
			.tag("level", metric.level().toString())
			.register(registry);
	}

	private static long currentValue(LogMetrics metrics, StandardMetric metric) {
		for (var counter : metrics.counters()) {
			if (counter.name().equals(metric.metricName()) && counter.level() == metric.level()) {
				return counter.count();
			}
		}
		return 0;
	}

}

package snippets;

import java.util.List;

import io.jstach.rainbowgum.LogConfig;
import io.jstach.rainbowgum.LogMetrics;

public class AlertsMetricsExample {

	// @start region = "alertsExample"
	/*
	 * Forwards every alert to your own alerting/paging system as it happens, in addition
	 * to it still being recorded in the ring buffer.
	 */
	public AutoCloseable subscribeToAlerts(LogConfig config) {
		return config.alerts().addListener(event -> sendToPagingSystem(event.message()));
	}
	// @end

	// @start region = "metricsExample"
	/*
	 * Binds every well known counter (see LogMetrics.StandardMetric) to your own metrics
	 * system without hand listing each name/level pair - a future new StandardMetric
	 * constant is picked up automatically.
	 */
	public void reportMetrics(LogConfig config) {
		List<LogMetrics.Counter> counters = config.metrics().counters();
		for (var metric : LogMetrics.StandardMetric.values()) {
			long value = counters.stream()
				.filter(c -> c.name().equals(metric.metricName()) && c.level() == metric.level())
				.mapToLong(LogMetrics.Counter::count)
				.findFirst()
				.orElse(0);
			reportToMetricsSystem(metric.metricName(), value);
		}
	}
	// @end

	private void sendToPagingSystem(String message) {
	}

	private void reportToMetricsSystem(String name, long value) {
	}

}

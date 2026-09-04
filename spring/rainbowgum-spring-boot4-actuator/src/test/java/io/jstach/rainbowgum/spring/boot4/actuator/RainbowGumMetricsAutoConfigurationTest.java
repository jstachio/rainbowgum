package io.jstach.rainbowgum.spring.boot4.actuator;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.junit.jupiter.api.parallel.Isolated;
import org.springframework.boot.micrometer.metrics.autoconfigure.CompositeMeterRegistryAutoConfiguration;
import org.springframework.boot.micrometer.metrics.autoconfigure.MetricsAutoConfiguration;
import org.springframework.boot.micrometer.metrics.autoconfigure.export.simple.SimpleMetricsExportAutoConfiguration;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

import io.jstach.rainbowgum.LogConfig;
import io.jstach.rainbowgum.LogMetrics;
import io.jstach.rainbowgum.RainbowGum;
import io.micrometer.core.instrument.MeterRegistry;

/*
 * Boots a real (if minimal) Spring context, registering the same
 * MetricsAutoConfiguration/CompositeMeterRegistryAutoConfiguration that a real Spring
 * Boot app with spring-boot-starter-micrometer-metrics on the classpath would run - not
 * just this module's own RainbowGumMetricsAutoConfiguration - so the whole chain
 * (MeterBinder bean created, then Spring Boot's own metrics machinery actually calling
 * MeterBinder#bindTo(registry)) runs for real rather than assuming it would. Also
 * exercises the JPMS module boundary (CGLIB enhancement of the auto configuration class,
 * see module-info's `opens`) which a plain unit test of RainbowGumMeterBinder would not.
 * <p>
 * Touches RainbowGum's static current-instance holder - see RainbowGumTest's identical
 * note (in rainbowgum-core) for why @Isolated/@Execution(SAME_THREAD) are needed.
 */
@Isolated
@Execution(ExecutionMode.SAME_THREAD)
class RainbowGumMetricsAutoConfigurationTest {

	@Test
	void countersAreBoundToMicrometer() throws Exception {
		LogConfig config = LogConfig.builder().build();
		try (var gum = RainbowGum.builder(config).set()) {
			config.metrics().errorCounter(LogMetrics.EVENTS_DROPPED_METRIC, 3);
			config.metrics().warnCounter(LogMetrics.BUFFER_TRIMMED_METRIC, 5);

			try (var context = new AnnotationConfigApplicationContext()) {
				context.register(CompositeMeterRegistryAutoConfiguration.class, MetricsAutoConfiguration.class,
						SimpleMetricsExportAutoConfiguration.class, RainbowGumMetricsAutoConfiguration.class);
				context.refresh();

				var registry = context.getBean(MeterRegistry.class);

				assertEquals(3.0,
						registry.get(RainbowGumMeterBinder.METRIC_PREFIX + LogMetrics.EVENTS_DROPPED_METRIC)
							.tag("level", "ERROR")
							.functionCounter()
							.count());
				assertEquals(5.0,
						registry.get(RainbowGumMeterBinder.METRIC_PREFIX + LogMetrics.BUFFER_TRIMMED_METRIC)
							.tag("level", "WARNING")
							.functionCounter()
							.count());

				config.metrics().errorCounter(LogMetrics.EVENTS_DROPPED_METRIC, 4);
				assertEquals(7.0,
						registry.get(RainbowGumMeterBinder.METRIC_PREFIX + LogMetrics.EVENTS_DROPPED_METRIC)
							.tag("level", "ERROR")
							.functionCounter()
							.count(),
						"a FunctionCounter reads the live value on each poll, not a snapshot taken at bind time");
			}
		}
	}

}

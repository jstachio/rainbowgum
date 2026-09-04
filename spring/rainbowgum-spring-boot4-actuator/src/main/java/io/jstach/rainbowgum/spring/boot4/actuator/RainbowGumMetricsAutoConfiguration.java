package io.jstach.rainbowgum.spring.boot4.actuator;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.micrometer.metrics.autoconfigure.CompositeMeterRegistryAutoConfiguration;
import org.springframework.boot.micrometer.metrics.autoconfigure.MetricsAutoConfiguration;
import org.springframework.context.annotation.Bean;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.binder.MeterBinder;

/**
 * Registers a {@link MeterBinder} that bridges RainbowGum's
 * {@link io.jstach.rainbowgum.LogMetrics} counters to Micrometer, when Micrometer is on
 * the classpath. Modeled directly on Spring Boot's own
 * {@code LogbackMetricsAutoConfiguration}/{@code Log4J2MetricsAutoConfiguration} in
 * {@code spring-boot-micrometer-metrics} - same {@code @AutoConfiguration(after = ...)}
 * ordering, same "one {@link MeterBinder} bean, let Spring Boot's own metrics
 * infrastructure apply it" shape.
 * <p>
 * This is deliberately its own module (not folded into
 * {@code rainbowgum-spring-boot4-starter}): pulling in Micrometer/metrics is an opt in
 * choice for applications that already have
 * {@code spring-boot-starter-micrometer-metrics} (the Spring Boot 4 starter - metrics
 * moved out of {@code spring-boot-starter-actuator} into its own starter in Boot 4), not
 * something every RainbowGum + Spring Boot user should get by default. Runs as a normal
 * {@link AutoConfiguration}, well after {@code RainbowGumLoggingSystemFactory} has
 * already bootstrapped {@link io.jstach.rainbowgum.RainbowGum} during the earlier,
 * bean-free {@code LoggingSystemFactory} phase - see that class for why the two can't be
 * combined into one step.
 */
@AutoConfiguration(after = { MetricsAutoConfiguration.class, CompositeMeterRegistryAutoConfiguration.class })
@ConditionalOnClass(MeterRegistry.class)
public class RainbowGumMetricsAutoConfiguration {

	/**
	 * Creates the auto configuration.
	 */
	public RainbowGumMetricsAutoConfiguration() {
	}

	/**
	 * Creates the meter binder bean. Spring Boot's own metrics autoconfiguration applies
	 * every {@link MeterBinder} bean to the {@link MeterRegistry} once one is available,
	 * so this class does not need to inject or depend on the registry directly.
	 * @return meter binder.
	 */
	@Bean
	public MeterBinder rainbowGumMeterBinder() {
		return new RainbowGumMeterBinder();
	}

}

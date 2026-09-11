package io.jstach.rainbowgum.benchmark.webapp;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import jakarta.servlet.Filter;

/**
 * Registers the shared benchmark controller and MDC filter. Each app's
 * {@code @SpringBootApplication} main class imports this explicitly rather than relying
 * on component scanning, since every app's main class necessarily lives in its own
 * package.
 */
@Configuration
public class BenchmarkWebConfig {

	/**
	 * For Spring.
	 */
	public BenchmarkWebConfig() {
	}

	@Bean
	public BenchController benchController() {
		return new BenchController();
	}

	@Bean
	public Filter requestIdFilter() {
		return new RequestIdFilter();
	}

}

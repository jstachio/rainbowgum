package io.jstach.rainbowgum;

import static org.junit.jupiter.api.Assertions.*;

import java.lang.System.Logger.Level;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.junit.jupiter.api.parallel.Isolated;

/*
 * systemPropertyStillWinsWhenAPropertiesProviderContributesSomething sets a real System
 * property (JVM-wide global state). @Isolated/SAME_THREAD keep this from racing any
 * other test class that reads/sets system properties under the "fast" profile's parallel
 * test execution (see SimplePropertiesTest's identical note for the same pattern).
 */
@Isolated
@Execution(ExecutionMode.SAME_THREAD)
class LogConfigTest {

	@Test
	void test() {
		var config = LogConfig.builder()
			.properties(Map.<String, String>of("logging.level.stuff", "DEBUG")::get)
			.build();
		var resolver = config.levelResolver();
		var actual = resolver.resolveLevel("stuff");

		assertEquals(Level.DEBUG, actual);
	}

	@Test
	void alertsErrorAlsoIncrementsAMetricsCounterNamedAfterTheLoggerName() {
		var config = LogConfig.builder().build();
		config.alerts().error(LogConfigTest.class, "first", new RuntimeException());
		config.alerts().error(LogConfigTest.class, "second", new RuntimeException());

		var counters = config.metrics().counters();
		assertEquals(1, counters.size());
		assertEquals(new LogMetrics.Counter(LogConfigTest.class.getName(), Level.ERROR, 2), counters.get(0));
	}

	/*
	 * Regression test: LogConfig.Builder.build() used to pass SYSTEM_PROPERTIES as
	 * LogProperties.of(List, LogProperties)'s *fallback* argument, which that method only
	 * ever consults when the list itself is empty. So the moment any PropertiesProvider
	 * (e.g. rainbowgum-micronaut5's own GLOBAL_CHANGE_PROPERTY layer) contributed
	 * anything at all, every -D system property override silently stopped working, with
	 * no error. A PropertiesProvider that contributes some unrelated property
	 * ("logging.other", not the one under test) reproduces exactly that shape: it must
	 * not cause "logging.level.stuff" to lose to the real system property set below.
	 */
	@Test
	void systemPropertyStillWinsWhenAPropertiesProviderContributesSomething() {
		System.setProperty("logging.level.stuff", "TRACE");
		try {
			var config = LogConfig.builder()
				.propertiesProvider(
						registry -> List.of((LogProperties) key -> "logging.other".equals(key) ? "1" : null))
				.build();
			var resolver = config.levelResolver();
			assertEquals(Level.TRACE, resolver.resolveLevel("stuff"));
		}
		finally {
			System.clearProperty("logging.level.stuff");
		}
	}

	@Test
	void loggerNamesEmptyByDefault() {
		var config = LogConfig.builder().build();
		assertEquals(Set.of(), loggerNames(config));
	}

	@Test
	void registerLoggerNameIsIdempotent() {
		var config = LogConfig.builder().build();
		var registry = config.loggerRegistry();
		registry.registerLoggerName(LoggerAPI.Standard.SLF4J, "com.example.Foo");
		registry.registerLoggerName(LoggerAPI.Standard.SLF4J, "com.example.Bar");
		registry.registerLoggerName(LoggerAPI.Standard.SLF4J, "com.example.Foo");

		assertEquals(Set.of("com.example.Foo", "com.example.Bar"), loggerNames(config));
	}

	@Test
	void registerLoggerNameIncrementsMetricOnceIfNewOnly() {
		var config = LogConfig.builder().build();
		var registry = config.loggerRegistry();
		registry.registerLoggerName(LoggerAPI.Standard.SLF4J, "com.example.Foo");
		registry.registerLoggerName(LoggerAPI.Standard.SYSTEM_LOGGER, "com.example.Bar");
		registry.registerLoggerName(LoggerAPI.Standard.SLF4J, "com.example.Foo");

		var counters = config.metrics().counters();
		assertEquals(List.of(new LogMetrics.Counter(LogMetrics.LOGGER_NAMES_METRIC, Level.INFO, 2)), counters);
	}

	/*
	 * LogConfig.LoggerRegistry deliberately does not expose the accumulated names itself
	 * (see its javadoc); downcasting to the package-private implementation is the same
	 * pattern DefaultLogReporter uses to reach it for the LOGGERS section.
	 */
	private static Set<String> loggerNames(LogConfig config) {
		return ((DefaultLoggerRegistry) config.loggerRegistry()).loggerNames();
	}

}

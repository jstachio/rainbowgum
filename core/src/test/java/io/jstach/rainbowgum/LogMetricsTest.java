package io.jstach.rainbowgum;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.System.Logger.Level;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

class LogMetricsTest {

	@ParameterizedTest
	@EnumSource(value = Level.class, names = { "INFO", "WARNING", "ERROR" })
	void gaugeHandlesAreDistinctAndShareTheNamedValue(Level level) {
		var metrics = LogConfig.builder().build().metrics();
		var first = metrics.gauge("queue", level);
		var second = metrics.gauge("queue", level);
		assertNotSame(first, second);
		assertEquals(List.of(new LogMetrics.Counter("queue", level, 0)), metrics.counters());
		first.increment();
		first.increment(4);
		second.decrement();
		second.decrement(2);
		assertEquals(List.of(new LogMetrics.Counter("queue", level, 2)), metrics.counters());
		first.decrement(3);
		assertEquals(List.of(new LogMetrics.Counter("queue", level, -1)), metrics.counters());
	}

	@ParameterizedTest
	@EnumSource(value = Level.class, names = { "ALL", "TRACE", "DEBUG", "OFF" })
	void disabledGaugeDoesNotRegisterOrUpdateMetrics(Level level) {
		var metrics = LogConfig.builder().build().metrics();
		var gauge = metrics.gauge("queue", level);
		assertSame(gauge, metrics.gauge("other", Level.DEBUG));
		assertEquals(List.of(), metrics.counters());
		metrics.infoCounter("queue", 3);
		gauge.increment();
		gauge.increment(4);
		gauge.decrement();
		gauge.decrement(2);
		assertEquals(List.of(new LogMetrics.Counter("queue", Level.INFO, 3)), metrics.counters());
	}

	@Test
	void gaugeSharesItsValueWithTheMatchingCounterMethod() {
		var metrics = LogConfig.builder().build().metrics();
		metrics.infoCounter("queue", 3);
		var gauge = metrics.gauge("queue", Level.INFO);
		gauge.decrement();
		metrics.infoCounter("queue", 2);
		assertEquals(List.of(new LogMetrics.Counter("queue", Level.INFO, 4)), metrics.counters());
	}

	@Test
	void errorCounterAccumulatesByName() {
		var metrics = LogConfig.builder().build().metrics();
		metrics.errorCounter("queue.dropped", 1);
		metrics.errorCounter("queue.dropped", 2);
		metrics.errorCounter("queue.overflow", 1);

		var counters = metrics.counters();
		assertEquals(2, counters.size());
		assertTrue(counters.contains(new LogMetrics.Counter("queue.dropped", Level.ERROR, 3)));
		assertTrue(counters.contains(new LogMetrics.Counter("queue.overflow", Level.ERROR, 1)));
	}

	@Test
	void warnCounterAccumulatesByNameSeparatelyFromErrorCounter() {
		var metrics = LogConfig.builder().build().metrics();
		metrics.errorCounter("buffer.trimmed", 1);
		metrics.warnCounter("buffer.trimmed", 2);

		var counters = metrics.counters();
		assertEquals(2, counters.size());
		assertTrue(counters.contains(new LogMetrics.Counter("buffer.trimmed", Level.ERROR, 1)));
		assertTrue(counters.contains(new LogMetrics.Counter("buffer.trimmed", Level.WARNING, 2)));
	}

	@Test
	void infoCounterAccumulatesByNameSeparatelyFromErrorAndWarnCounters() {
		var metrics = LogConfig.builder().build().metrics();
		metrics.errorCounter("logger.names", 1);
		metrics.warnCounter("logger.names", 2);
		metrics.infoCounter("logger.names", 3);
		metrics.infoCounter("logger.names", 4);

		var counters = metrics.counters();
		assertEquals(3, counters.size());
		assertTrue(counters.contains(new LogMetrics.Counter("logger.names", Level.ERROR, 1)));
		assertTrue(counters.contains(new LogMetrics.Counter("logger.names", Level.WARNING, 2)));
		assertTrue(counters.contains(new LogMetrics.Counter("logger.names", Level.INFO, 7)));
	}

}

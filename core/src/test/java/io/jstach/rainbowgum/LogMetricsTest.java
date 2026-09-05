package io.jstach.rainbowgum;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.System.Logger.Level;

import org.junit.jupiter.api.Test;

class LogMetricsTest {

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

}

package io.jstach.rainbowgum.slf4j;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.slf4j.event.Level;

import io.jstach.rainbowgum.LogEvent;
import io.jstach.rainbowgum.LogEventLogger;

public class LoggerTest {

	@Test
	public void testErrorLogger() {
		LogEventLogger appender = e -> {
			StringBuilder sb = new StringBuilder();
			e.formattedMessage(sb);
			System.out.append(sb);
		};
		var handler = LogEventHandler.of("stuff", appender, new RainbowGumMDCAdapter());
		var logger = LevelLogger.of(Level.ERROR, handler);

		logger.error("Crap {} {} {}", "1", "2", "3");

		logger.trace("No Crap {} {} {}", "1", "2", "3");

	}

	@Test
	public void testKeyValuesUnaffectedByLaterMdcMutation() {
		/*
		 * The plain (non fluent builder) logging path hands out the live,
		 * ThreadLocal-owned MDC container as-is - no copy at this layer. What keeps a
		 * previously handed-out reference safe from a later MDC.put/remove on the same
		 * thread is ArrayMDCAdapter's own copy-on-write tracking: keyValuesOrNull() marks
		 * the container as "just exposed", and the next put/remove clones before mutating
		 * instead of mutating in place. This test is really exercising that COW behavior
		 * through the handler, not a copy made here.
		 */
		var mdc = new RainbowGumMDCAdapter();
		mdc.put("phase", "A");

		List<LogEvent> captured = new ArrayList<>();
		LogEventLogger appender = captured::add;
		var handler = LogEventHandler.of("stuff", appender, mdc);
		var logger = LevelLogger.of(Level.INFO, handler);

		logger.info("start");

		// simulate the calling thread moving on to a new logical unit of work
		// before an async worker thread would have gotten around to formatting
		// the captured event.
		mdc.put("phase", "B");
		mdc.put("extra", "new");

		assertEquals(1, captured.size());
		var kvs = captured.get(0).keyValues();
		assertEquals("A", kvs.getValueOrNull("phase"));
		assertEquals(null, kvs.getValueOrNull("extra"));
	}

}

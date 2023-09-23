package io.jstach.rainbowgum.slf4j;

import org.junit.jupiter.api.Test;
import org.slf4j.event.Level;

import io.jstach.rainbowgum.LogEventLogger;

public class LoggerTest {

	@Test
	public void testErrorLogger() {
		LogEventLogger appender = e -> {
			System.out.println(e.formattedMessage());
		};
		var logger = LevelLogger.of(Level.ERROR, "stuff", appender);

		logger.error("Crap {} {} {}", "1", "2", "3");

		logger.trace("No Crap {} {} {}", "1", "2", "3");

	}

}

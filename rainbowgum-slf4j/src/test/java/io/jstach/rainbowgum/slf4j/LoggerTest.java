package io.jstach.rainbowgum.slf4j;

import org.junit.jupiter.api.Test;
import org.slf4j.event.Level;

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

}

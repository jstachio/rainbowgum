package io.jstach.rainbowgum.slf4j;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.slf4j.spi.NOPLoggingEventBuilder.singleton;

import org.junit.jupiter.api.Test;
import org.slf4j.event.Level;

/*
 * BaseLogger (which OffLogger implements) overrides atTrace()/atDebug()/atInfo()/
 * atWarn()/atError() to return NOPLoggingEventBuilder.singleton() directly, without
 * ever calling makeLoggingEventBuilder(Level) - and SLF4J's own Logger.atLevel(Level)
 * default dispatches to those same atXxx() overrides. So no path through the SLF4J
 * Logger interface ever reaches OffLogger.makeLoggingEventBuilder(Level) or
 * withDepth(int) (nothing ever re-wraps an off logger at a different depth either).
 * Both are tested directly since there is no real caller to route through.
 */
class LevelLoggerOffLoggerTest {

	@Test
	void testMakeLoggingEventBuilderReturnsNop() {
		var logger = new LevelLogger.OffLogger("off");
		assertSame(singleton(), logger.makeLoggingEventBuilder(Level.INFO));
	}

	@Test
	void testWithDepthIsANoop() {
		var logger = new LevelLogger.OffLogger("off");
		assertSame(logger, logger.withDepth(5));
	}

	@Test
	void testLoggerName() {
		var logger = new LevelLogger.OffLogger("off");
		assertEquals("off", logger.getName());
	}

}

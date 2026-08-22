package io.jstach.rainbowgum.slf4j;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.slf4j.event.Level;

import io.jstach.rainbowgum.LogEvent;
import io.jstach.rainbowgum.LogEvent.Caller;

/*
 * Each LevelLogger.XxxLogger record's withDepth(int) is only ever called via
 * ReplaceableLogger.withDepth() (see ReplaceableLoggerTest), which was only
 * exercised at Level.INFO, leaving TraceLogger/DebugLogger/WarnLogger/ErrorLogger's
 * own withDepth() overrides uncovered even though InfoLogger's was not.
 */
class LevelLoggerWithDepthTest {

	@ParameterizedTest
	@EnumSource(Level.class)
	void testWithDepthPreservesLevelAndName(Level level) {
		LogEventHandler handler = LogEventHandler.of("name", e -> {
		}, new RainbowGumMDCAdapter());
		var logger = LevelLogger.of(level, handler);
		var deeper = logger.withDepth(3);
		assertEquals(logger.getName(), deeper.getName());
		assertEquals(logger.level(), deeper.level());
		assertEquals(logger.getClass(), deeper.getClass());
	}

	// Levels is a static utility class, never explicitly instantiated anywhere.
	@Test
	void testLevelsIsInstantiable() {
		assertNotNull(new Levels());
	}

	/*
	 * The default handle(LogEvent, Caller) on LogEventHandler (which just ignores the
	 * caller and delegates to handle(LogEvent)) exists for handlers that are not
	 * caller-aware. CallerInfoEventDecorator overrides it; every real caller that cares
	 * about caller info goes through that override, so the default itself is never
	 * reached by anything in the suite.
	 */
	@Test
	void testHandleWithCallerDefaultIgnoresCallerAndDelegates() {
		LogEvent[] received = new LogEvent[1];
		LogEventHandler handler = LogEventHandler.of("name", e -> received[0] = e, new RainbowGumMDCAdapter());
		LogEvent event = handler.event0(Level.INFO, "hello");
		Caller caller = Caller.ofDepthOrNull(0);
		handler.handle(event, caller);
		assertEquals(event, received[0]);
	}

}

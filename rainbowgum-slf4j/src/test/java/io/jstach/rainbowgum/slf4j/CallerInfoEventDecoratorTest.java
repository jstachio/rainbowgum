package io.jstach.rainbowgum.slf4j;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.junit.jupiter.api.Test;
import org.slf4j.event.Level;

import io.jstach.rainbowgum.LogEvent;

/*
 * LoggerMethodTest/AbstractFilteringLoggerTest/ReplaceableLoggerTest already exercise
 * CallerInfoEventDecorator's handle(Level, ...) overloads (the actual dispatch path
 * for real logger calls) and withDepth(int) extensively. The pieces left uncovered
 * were the 3-arg convenience constructor and the public handle(LogEvent, int) method,
 * neither of which has any caller anywhere in the repo, and the caller-not-found
 * branch of withCaller(event, caller) - none of the above ever pass a depth large
 * enough for the stack walk to come up empty.
 */
class CallerInfoEventDecoratorTest {

	LogEvent lastEvent;

	@Test
	void testThreeArgConstructorDefaultsToCallerDepthDelta() {
		// CALLER_DEPTH_DELTA is calibrated for the normal dispatch path (through
		// LevelLogger, as every real caller uses), not for handle(Level, ...) called
		// directly - route through LevelLogger.of(...) like the rest of the suite
		// does rather than assert on a depth this constructor was never meant for.
		var decorator = new CallerInfoEventDecorator("test", new RainbowGumMDCAdapter(), e -> lastEvent = e);
		var logger = LevelLogger.of(Level.INFO, decorator);
		logger.info("hello");
		var caller = lastEvent.callerOrNull();
		assertEquals("testThreeArgConstructorDefaultsToCallerDepthDelta", caller.methodName());
		assertEquals("io.jstach.rainbowgum.slf4j.CallerInfoEventDecoratorTest", caller.className());
	}

	@Test
	void testHandleWithExplicitDepthAttributesTheDirectCaller() {
		var decorator = new CallerInfoEventDecorator("test", new RainbowGumMDCAdapter(), e -> lastEvent = e);
		LogEvent event = decorator.event0(Level.INFO, "hello");
		decorator.handle(event, 1);
		var caller = lastEvent.callerOrNull();
		assertEquals("testHandleWithExplicitDepthAttributesTheDirectCaller", caller.methodName());
		assertEquals("io.jstach.rainbowgum.slf4j.CallerInfoEventDecoratorTest", caller.className());
	}

	@Test
	void testHandleWithDepthPastStackTopAttachesNoCaller() {
		var decorator = new CallerInfoEventDecorator("test", new RainbowGumMDCAdapter(), e -> lastEvent = e);
		LogEvent event = decorator.event0(Level.INFO, "hello");
		decorator.handle(event, Integer.MAX_VALUE / 2);
		assertNull(lastEvent.callerOrNull());
		assertEquals(event, lastEvent);
	}

}

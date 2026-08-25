package io.jstach.rainbowgum.slf4j;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import org.slf4j.event.Level;

import io.jstach.rainbowgum.LogEvent;
import io.jstach.rainbowgum.LogEvent.Caller;
import io.jstach.rainbowgum.output.ListLogOutput;

/*
 * ReplaceableLogger backs every dynamically level-changeable logger
 * (RainbowGumLoggerFactory's ChangeType.LEVEL branch), but withDepth(int) itself is
 * never called anywhere in the framework or by any built-in decorator - it exists
 * purely as part of the DepthAwareLogger contract for third-party decorator authors
 * composing their own extra wrapping layer on top of a changeable logger. Tested
 * directly here since nothing else exercises it.
 */
class ReplaceableLoggerTest {

	ListLogOutput list = new ListLogOutput();

	LogEvent lastEvent;

	LogEventHandler handler = LogEventHandler.ofCallerInfo("test", e -> lastEvent = e, new RainbowGumMDCAdapter(), 1);

	@Test
	void testDelegateAndToString() {
		var logger = ReplaceableLogger.of(Level.INFO, handler);
		assertInstanceOf(LevelLogger.class, logger.delegate());
		assertTrue(logger.toString().contains("ReplaceableLogger"), logger.toString());
	}

	@Test
	void testSetLevelChangesEnablement() {
		var logger = ReplaceableLogger.of(Level.INFO, handler);
		assertTrue(logger.isInfoEnabled());
		assertFalse(logger.isDebugEnabled());
		logger.setLevel(Level.DEBUG);
		assertTrue(logger.isDebugEnabled());
	}

	@Test
	void testSetEventHandlerChangesWhereLogCallsGo() {
		var logger = ReplaceableLogger.of(Level.INFO, handler);
		logger.info("via original handler");
		assertEquals("test", lastEvent.loggerName());

		LogEvent[] rebound = new LogEvent[1];
		LogEventHandler newHandler = LogEventHandler.ofCallerInfo("rebound", e -> rebound[0] = e,
				new RainbowGumMDCAdapter(), 1);
		logger.setEventHandler(newHandler);
		logger.info("via rebound handler");
		assertEquals("via rebound handler", rebound[0].message());
		assertEquals("rebound", rebound[0].loggerName());
	}

	@Test
	void testWithDepthCompensatesForOneExtraWrappingLayer() {
		// depth=1 here mirrors RainbowGumLoggerFactory's own ChangeType.LEVEL branch,
		// which already compensates for ReplaceableLogger's own ForwardingLogger
		// indirection by passing depth=1 (not 0) to maybeAddCallerInfo. Calling
		// directly at this depth must attribute this test method as caller.
		var logger = ReplaceableLogger.of(Level.INFO, handler);
		logger.info("direct");
		Caller direct = lastEvent.callerOrNull();
		assertEquals("testWithDepthCompensatesForOneExtraWrappingLayer", direct.methodName());
		assertEquals("io.jstach.rainbowgum.slf4j.ReplaceableLoggerTest", direct.className());

		// withDepth(int) is absolute (CALLER_DEPTH_DELTA + depth), not additive to
		// whatever depth the current handler already has - depth=2 here is one more
		// than the depth=1 baseline above, compensating for logThroughOneExtraFrame
		// below adding exactly one more layer of indirection.
		var deeper = logger.withDepth(2);
		assertInstanceOf(ReplaceableLogger.class, deeper);
		logThroughOneExtraFrame(deeper);
		Caller indirect = lastEvent.callerOrNull();
		assertEquals("testWithDepthCompensatesForOneExtraWrappingLayer", indirect.methodName());
		assertEquals("io.jstach.rainbowgum.slf4j.ReplaceableLoggerTest", indirect.className());
	}

	private void logThroughOneExtraFrame(org.slf4j.Logger logger) {
		logger.info("indirect");
	}

}

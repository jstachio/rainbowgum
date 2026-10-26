package io.jstach.rainbowgum.slf4j;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.jspecify.annotations.Nullable;
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

	@Nullable LogEvent lastEvent;

	LogEventHandler handler = LogEventHandler.ofCallerInfo("test", e -> lastEvent = e, new RainbowGumMDCAdapter(), 1,
			NoopKeyValuesContributor.INSTANCE);

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
		assertNotNull(lastEvent);
		assertEquals("test", lastEvent.loggerName());

		LogEvent[] rebound = new LogEvent[1];
		LogEventHandler newHandler = LogEventHandler.ofCallerInfo("rebound", e -> rebound[0] = e,
				new RainbowGumMDCAdapter(), 1, NoopKeyValuesContributor.INSTANCE);
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
		assertNotNull(lastEvent);
		Caller direct = lastEvent.callerOrNull();
		assertNotNull(direct);
		assertEquals("testWithDepthCompensatesForOneExtraWrappingLayer", direct.methodName());
		assertEquals("io.jstach.rainbowgum.slf4j.ReplaceableLoggerTest", direct.className());

		// withDepth(int) is absolute (CALLER_DEPTH_DELTA + depth), not additive to
		// whatever depth the current handler already has - depth=2 here is one more
		// than the depth=1 baseline above, compensating for logThroughOneExtraFrame
		// below adding exactly one more layer of indirection.
		var deeper = logger.withDepth(2);
		assertInstanceOf(ReplaceableLogger.class, deeper);
		logThroughOneExtraFrame(deeper);
		assertNotNull(lastEvent);
		Caller indirect = lastEvent.callerOrNull();
		assertNotNull(indirect);
		assertEquals("testWithDepthCompensatesForOneExtraWrappingLayer", indirect.methodName());
		assertEquals("io.jstach.rainbowgum.slf4j.ReplaceableLoggerTest", indirect.className());
	}

	@Test
	void offStateSurvivesHandlerAndDepthChanges() {
		var logger = ReplaceableLogger.of(System.Logger.Level.OFF, handler);
		assertFalse(logger.isErrorEnabled());
		logger.setEventHandler(handler.withDepth(2));
		var deeper = logger.withDepth(2);
		assertFalse(deeper.isErrorEnabled());
		logger.error("disabled");
		assertEquals(null, lastEvent);
		logger.setLevel(System.Logger.Level.INFO);
		assertTrue(logger.isInfoEnabled());
	}

	@Test
	void setReplacesLevelAndHandlerTogether() {
		var logger = ReplaceableLogger.of(Level.INFO, handler);
		var deeper = handler.withDepth(2);
		logger.set(System.Logger.Level.OFF, deeper);
		assertFalse(logger.isErrorEnabled());
		assertEquals(deeper, logger.currentHandler());
		logger.set(System.Logger.Level.DEBUG, handler);
		assertTrue(logger.isDebugEnabled());
		assertFalse(logger.isTraceEnabled());
		assertEquals(handler, logger.currentHandler());
	}

	private void logThroughOneExtraFrame(org.slf4j.Logger logger) {
		logger.info("indirect");
	}

}

package io.jstach.rainbowgum.jul.logmanager;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.util.logging.Level;
import java.util.logging.LogManager;
import java.util.logging.Logger;

import org.junit.jupiter.api.Test;

import io.jstach.rainbowgum.LogFormatter;
import io.jstach.rainbowgum.LogRouter.Router.RouterFactory;
import io.jstach.rainbowgum.RainbowGum;
import io.jstach.rainbowgum.output.ListLogOutput;

/*
 * Every test class in this module gets its own fresh, non-reused JVM fork (see this
 * module's pom.xml) with -Djava.util.logging.manager=RainbowGumLogManager set as a real
 * "-D" JVM argument, present before the fork executes a single instruction. That is the
 * only way to prove this actually works: java.util.logging.LogManager resolves that
 * system property exactly once, via a static bootstrap triggered by the first thing in
 * the JVM that touches java.util.logging, so setting it programmatically with
 * System.setProperty(...) from inside a running test (in a JVM shared with other tests,
 * some of which have almost certainly already touched java.util.logging themselves)
 * would either be a no-op or, worse, pass only by accident depending on test order.
 */
class RainbowGumLogManagerTest {

	@Test
	void testRainbowGumLogManagerIsInstalled() {
		assertEquals(RainbowGumLogManager.class, LogManager.getLogManager().getClass());
	}

	@Test
	void testGetLoggerReturnsARainbowGumJULLogger() {
		var logger = Logger.getLogger("some.logger.name");
		assertEquals(RainbowGumJULLogger.class, logger.getClass());
	}

	@Test
	void testGetLoggerReturnsTheSameInstanceForTheSameName() {
		assertSame(Logger.getLogger("same.name"), Logger.getLogger("same.name"));
	}

	@Test
	void testAddLoggerAlwaysReturnsFalse() {
		assertFalse(LogManager.getLogManager().addLogger(new Logger("not.bridged", null) {
		}));
	}

	/*
	 * A java.util.logging.LogManager is genuinely JVM-wide, so once RainbowGum.set()
	 * makes this test's own RainbowGum the global one, anything else in this JVM that
	 * happens to log via java.util.logging (JUnit Platform's own launcher internals do,
	 * for example) is routed through it too, interleaved with this test's own line.
	 * Assert on the presence of the exact line this test produced, not on the output
	 * being only that line.
	 */
	@Test
	void testLoggingRoutesThroughRainbowGum() {
		ListLogOutput output = new ListLogOutput();
		try (var gum = rainbowGum(output, System.Logger.Level.INFO)) {
			var logger = Logger.getLogger("test.jul.logmanager");
			logger.info("Hello from JUL LogManager!");
			String expected = "INFO test.jul.logmanager Hello from JUL LogManager!\n";
			assertTrue(output.toString().contains(expected),
					() -> "expected output to contain: " + expected + "\nactual output was: " + output);
		}
	}

	/*
	 * isLoggable(Level) (and therefore log(LogRecord), which checks it first) asks
	 * Rainbow Gum's own level resolution on every call rather than consulting a level
	 * cached on the Logger object - proven here by a level that disables the call
	 * outright rather than by a lower threshold that would still let it through.
	 */
	@Test
	void testDisabledLevelIsNotRouted() {
		ListLogOutput output = new ListLogOutput();
		try (var gum = rainbowGum(output, System.Logger.Level.WARNING)) {
			var logger = Logger.getLogger("test.jul.logmanager.disabled");
			assertFalse(logger.isLoggable(Level.INFO));
			assertTrue(logger.isLoggable(Level.WARNING));
			logger.info("should not be routed");
			assertFalse(output.toString().contains("should not be routed"));
		}
	}

	@Test
	void testSetLevelAndSetParentAreNoOpsNotExceptions() {
		var logger = Logger.getLogger("test.jul.logmanager.noop");
		logger.setLevel(Level.SEVERE);
		logger.setParent(Logger.getLogger(""));
	}

	private static RainbowGum rainbowGum(ListLogOutput output, System.Logger.Level level) {
		return RainbowGum.builder().route(r -> {
			r.appender("list", a -> {
				a.output(output);
				a.formatter(LogFormatter.builder().level().space().loggerName().space().message().newline().build());
			});
			r.level(level);
			r.factory(RouterFactory.of(e -> e.freeze(Instant.EPOCH)));
		}).set();
	}

}

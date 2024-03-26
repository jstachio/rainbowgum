package io.jstach.rainbowgum.test.jdk;

import static org.junit.jupiter.api.Assertions.*;

import java.lang.System.Logger.Level;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;
import java.util.logging.Logger;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import io.jstach.rainbowgum.LogFormatter.LevelFormatter;
import io.jstach.rainbowgum.RainbowGum;
import io.jstach.rainbowgum.output.ListLogOutput;

class JDKSetupTest {

	/*
	 * The lock here is to allow concurrent testing in the future.
	 *
	 * The issue is we do not want to un/bind rainbowgum in the middle of another test.
	 */
	private static final ReentrantLock lock = new ReentrantLock();

	@Test
	void testSystemLoggingFactory() throws InterruptedException {
		doInLock(this::_systemLoggingFactory);
	}

	@ParameterizedTest
	@MethodSource("provideParameters")
	void testJULMessage(System.Logger.Level level, System.Logger.Level loggerLevel) throws InterruptedException {
		doInLock(() -> {
			_testJULMessage(level, loggerLevel);
		});

	}

	void _testJULMessage(System.Logger.Level level, System.Logger.Level loggerLevel) {
		ListLogOutput output = new ListLogOutput();
		String message = "Hello!";

		var jul = Logger.getLogger("after.load");
		try (var gum = JDKSetup.run(output, loggerLevel)) {
			var julLevel = julLevel(level);
			jul.log(julLevel, message);
			switch (level) {
				case TRACE -> jul.finest(message);
				case DEBUG -> jul.fine(message);
				case INFO -> jul.info(message);
				case WARNING -> jul.warning(message);
				case ERROR -> jul.severe(message);
				case ALL -> jul.log(julLevel, message);
				case OFF -> jul.log(julLevel, message);
			}
			var levelString = LevelFormatter.of().formatLevel(level);
			String expected;
			if (isEnabled(level, loggerLevel)) {
				expected = """
						00:00:00.000 [main] %s after.load - Hello!
						00:00:00.000 [main] %s after.load - Hello!
						""".formatted(levelString, levelString);
			}
			else {
				expected = "";
			}
			String actual = output.toString();
			assertEquals(expected, actual);

		}

	}

	private static boolean isEnabled(System.Logger.Level level, System.Logger.Level loggerLevel) {
		if (level == System.Logger.Level.ALL) {
			level = System.Logger.Level.TRACE;
		}
		if (loggerLevel == System.Logger.Level.ALL) {
			loggerLevel = System.Logger.Level.TRACE;
		}
		if (loggerLevel == System.Logger.Level.OFF) {
			return false;
		}
		if (level == loggerLevel) {
			return true;
		}
		return level.compareTo(loggerLevel) >= 0;
	}

	private java.util.logging.Level julLevel(System.Logger.Level level) {
		var julLevel = switch (level) {
			case TRACE -> java.util.logging.Level.FINEST;
			case DEBUG -> java.util.logging.Level.FINE;
			case INFO -> java.util.logging.Level.INFO;
			case WARNING -> java.util.logging.Level.WARNING;
			case ERROR -> java.util.logging.Level.SEVERE;
			case ALL -> java.util.logging.Level.ALL;
			case OFF -> java.util.logging.Level.OFF;
		};
		return julLevel;
	}

	private static Stream<Arguments> provideParameters() {
		List<Arguments> args = new ArrayList<>();
		for (var level : System.Logger.Level.values()) {
			for (var loggerLevel : System.Logger.Level.values()) {
				args.add(Arguments.of(level, loggerLevel));
			}
		}
		return args.stream();
	}

	void doNothing() {

	}

	interface JULMessageMethod {

		String test(Logger logger, String message);

	}

	private static void doInLock(Runnable r) throws InterruptedException {
		if (lock.tryLock(2, TimeUnit.SECONDS)) {
			try {
				r.run();
			}
			finally {
				lock.unlock();
			}
		}
		else {
			fail("Lock timed out");
		}
	}

	private void _systemLoggingFactory() {
		var logger = System.getLogger("before.load");
		logger.log(Level.INFO, "Hello {0} from System.Logger!", "Gum");
		var jul = Logger.getLogger("before.load");
		jul.log(java.util.logging.Level.INFO, "Hello {0} from JUL Logger!", "Gum");
		assertNull(RainbowGum.getOrNull(), "Rainbow Gum should not be loaded yet.");
		ListLogOutput output = new ListLogOutput();
		try (var gum = JDKSetup.run(output, System.Logger.Level.INFO)) {
			assertNotNull(RainbowGum.getOrNull());
			String actual = output.toString();
			String expected = """
					00:00:00.000 [main] INFO before.load - Hello Gum from System.Logger!
					00:00:00.000 [main] INFO before.load - Hello Gum from JUL Logger!
					""";
			assertEquals(expected, actual);
		}
		assertNull(RainbowGum.getOrNull(), "Rainbow Gum should not be loaded anymore.");
	}

}

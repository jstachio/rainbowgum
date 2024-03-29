package io.jstach.rainbowgum.test.jdk;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import java.lang.System.Logger.Level;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;
import java.util.logging.Logger;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.junit.jupiter.api.MethodOrderer.OrderAnnotation;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;

import io.jstach.rainbowgum.LogFormatter.LevelFormatter;
import io.jstach.rainbowgum.jdk.jul.JULConfigurator;
import io.jstach.rainbowgum.LogProperties;
import io.jstach.rainbowgum.RainbowGum;
import io.jstach.rainbowgum.output.ListLogOutput;

@TestMethodOrder(OrderAnnotation.class)
class JDKSetupTest {

	/*
	 * The lock here is to allow concurrent testing in the future. Its unclear if junit
	 * method order will be respected.
	 *
	 * The issue is we do not want to un/bind rainbowgum in the middle of another test.
	 * This is because the jul and system logger are essentially global static singletons
	 * that use the global router.
	 */
	private static final ReentrantLock lock = new ReentrantLock();

	/*
	 * This test needs to go first as it tests some initialization that cannot be undone
	 * without forking namely loading the system logger.
	 */
	@Order(1)
	@Test
	void testSystemLoggingFactory() throws InterruptedException {
		doInLock(this::_systemLoggingFactory);
	}

	@Order(2)
	@ParameterizedTest
	@MethodSource("provideLevels")
	void testJULMessage(System.Logger.Level level, System.Logger.Level loggerLevel) throws InterruptedException {
		doInLock(() -> {
			_testMessage(JULLoggerTester.JUL, level, loggerLevel);
		});

	}

	@Order(2)
	@ParameterizedTest
	@MethodSource("provideLevels")
	void testSystemLoggerMessage(System.Logger.Level level, System.Logger.Level loggerLevel)
			throws InterruptedException {
		doInLock(() -> {
			_testMessage(SystemLoggerTester.SYSTEM_LOGGER, level, loggerLevel);
		});

	}

	@Order(3)
	@ParameterizedTest
	@MethodSource("provideOneArg")
	void testJULOneArg(System.Logger.Level level, System.Logger.Level loggerLevel, Arg arg)
			throws InterruptedException {
		doInLock(() -> {
			_testOneArg(JULLoggerTester.JUL, level, loggerLevel, arg);
		});

	}

	@Order(4)
	@ParameterizedTest
	@MethodSource("provideOneArg")
	void testSystemLoggerOneArg(System.Logger.Level level, System.Logger.Level loggerLevel, Arg arg)
			throws InterruptedException {
		doInLock(() -> {
			_testOneArg(SystemLoggerTester.SYSTEM_LOGGER, level, loggerLevel, arg);
		});

	}

	@Order(5)
	@ParameterizedTest
	@MethodSource("provideOneArg")
	void testJULTwoArgs(System.Logger.Level level, System.Logger.Level loggerLevel, Arg arg)
			throws InterruptedException {
		doInLock(() -> {
			_testTwoArgs(JULLoggerTester.JUL, level, loggerLevel, arg);
		});

	}

	@Order(6)
	@ParameterizedTest
	@MethodSource("provideOneArg")
	void testSystemLoggerTwoArgs(System.Logger.Level level, System.Logger.Level loggerLevel, Arg arg)
			throws InterruptedException {
		doInLock(() -> {
			_testTwoArgs(SystemLoggerTester.SYSTEM_LOGGER, level, loggerLevel, arg);
		});

	}

	@Order(7)
	@ParameterizedTest
	@MethodSource("provideOneArg")
	void testJULThreeArgs(System.Logger.Level level, System.Logger.Level loggerLevel, Arg arg)
			throws InterruptedException {
		doInLock(() -> {
			_testThreeArgs(JULLoggerTester.JUL, level, loggerLevel, arg);
		});

	}

	@Order(8)
	@ParameterizedTest
	@MethodSource("provideOneArg")
	void testSystemLoggerThreeArgs(System.Logger.Level level, System.Logger.Level loggerLevel, Arg arg)
			throws InterruptedException {
		doInLock(() -> {
			_testThreeArgs(SystemLoggerTester.SYSTEM_LOGGER, level, loggerLevel, arg);
		});

	}

	@Order(9)
	@ParameterizedTest
	@MethodSource("provideLevels")
	void testJULThrowable(System.Logger.Level level, System.Logger.Level loggerLevel) throws InterruptedException {
		doInLock(() -> {
			_testThrowable(JULLoggerTester.JUL, level, loggerLevel);
		});
	}

	@Order(10)
	@ParameterizedTest
	@MethodSource("provideLevels")
	void testSystemLoggerThrowable(System.Logger.Level level, System.Logger.Level loggerLevel)
			throws InterruptedException {
		doInLock(() -> {
			_testThrowable(SystemLoggerTester.SYSTEM_LOGGER, level, loggerLevel);
		});
	}

	@Test
	@Order(11)
	void testOffForCeki() throws InterruptedException {
		doInLock(() -> {
			_testThrowable(JULLoggerTester.JUL, System.Logger.Level.OFF, System.Logger.Level.TRACE);
		});
	}

	@Order(12)
	@ParameterizedTest
	@EnumSource(System.Logger.Level.class)
	void testNullRecord(System.Logger.Level loggerLevel) throws InterruptedException {
		doInLock(() -> {
			ListLogOutput output = new ListLogOutput();
			var tester = JULLoggerTester.JUL;
			try (var gum = JDKSetup.run(output, loggerLevel)) {
				var logger = tester.logger("");
				var handlers = logger.getHandlers();
				assertEquals(1, handlers.length);
				handlers[0].publish(null);
				String expected = "";
				String actual = output.toString();
				assertEquals(expected, actual);
			}
		});
	}

	interface LoggerProvider<T> {

		T logger(String loggerName);

	}

	interface LoggerTester<T> extends LoggerProvider<T> {

		public void message(T logger, System.Logger.Level level, String message);

		public void oneArg(T logger, System.Logger.Level level, String message, Arg arg);

		public void twoArgs(T logger, System.Logger.Level level, String message, Arg arg1, Arg arg2);

		public void threeArgs(T logger, System.Logger.Level level, String message, Arg arg1, Arg arg2, Arg arg3);

		public void throwable(T logger, System.Logger.Level level, String message, Throwable throwable);

	}

	enum JULLoggerTester implements LoggerTester<java.util.logging.Logger> {

		JUL;

		public java.util.logging.Logger logger(String loggerName) {
			return Logger.getLogger(loggerName);
		}

		public void message(java.util.logging.Logger logger, System.Logger.Level level, String message) {
			logger.log(julLevel(level), message);
		}

		public void oneArg(java.util.logging.Logger logger, System.Logger.Level level, String message, Arg arg) {
			logger.log(julLevel(level), message, arg.arg);
		}

		public void twoArgs(java.util.logging.Logger logger, System.Logger.Level level, String message, Arg arg1,
				Arg arg2) {
			logger.log(julLevel(level), message, new Object[] { arg1.arg, arg2.arg });
		}

		public void threeArgs(java.util.logging.Logger logger, System.Logger.Level level, String message, Arg arg1,
				Arg arg2, Arg arg3) {
			logger.log(julLevel(level), message, new Object[] { arg1.arg, arg2.arg, arg3.arg });
		}

		public void throwable(java.util.logging.Logger logger, System.Logger.Level level, String message,
				Throwable throwable) {
			logger.log(julLevel(level), message, throwable);

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

	}

	enum SystemLoggerTester implements LoggerTester<System.Logger> {

		SYSTEM_LOGGER;

		@Override
		public java.lang.System.Logger logger(String loggerName) {
			return System.getLogger(loggerName);
		}

		@Override
		public void message(java.lang.System.Logger logger, Level level, String message) {
			logger.log(level, message);
		}

		@Override
		public void oneArg(java.lang.System.Logger logger, Level level, String message, Arg arg) {
			logger.log(level, message, arg.arg);
		}

		@Override
		public void twoArgs(java.lang.System.Logger logger, Level level, String message, Arg arg1, Arg arg2) {
			logger.log(level, message, arg1.arg, arg2.arg);
		}

		@Override
		public void threeArgs(java.lang.System.Logger logger, Level level, String message, Arg arg1, Arg arg2,
				Arg arg3) {
			logger.log(level, message, arg1.arg, arg2.arg, arg3.arg);
		}

		@Override
		public void throwable(java.lang.System.Logger logger, Level level, String message, Throwable throwable) {
			logger.log(level, message, throwable);
		}

	}

	<T> void _testMessage(LoggerTester<T> tester, System.Logger.Level level, System.Logger.Level loggerLevel) {
		ListLogOutput output = new ListLogOutput();
		String message = "Hello!";

		var logger = tester.logger("after.load");
		try (var gum = JDKSetup.run(output, loggerLevel)) {
			tester.message(logger, level, message);
			var levelString = LevelFormatter.of().formatLevel(level);
			String expected;
			if (isEnabled(level, loggerLevel)) {
				expected = """
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

	<T> void _testOneArg(LoggerTester<T> tester, System.Logger.Level level, System.Logger.Level loggerLevel, Arg arg) {
		ListLogOutput output = new ListLogOutput();

		String message = "Hello {0}!";

		try (var gum = JDKSetup.run(output, loggerLevel)) {
			var logger = tester.logger("after.load");
			tester.oneArg(logger, level, message, arg);
			var levelString = LevelFormatter.of().formatLevel(level);
			String expected;
			if (isEnabled(level, loggerLevel)) {
				if (arg == Arg.BAD) {
					expected = """
							00:00:00.000 [main] %s after.load - Hello {0}! %s
							""".formatted(levelString, arg.expected);
				}
				else {
					expected = """
							00:00:00.000 [main] %s after.load - Hello %s!
							""".formatted(levelString, arg.expected);
				}
			}
			else {
				expected = "";
			}
			String actual = output.toString();
			assertEquals(expected, actual);

		}

	}

	<T> void _testTwoArgs(LoggerTester<T> tester, System.Logger.Level level, System.Logger.Level loggerLevel, Arg arg) {
		ListLogOutput output = new ListLogOutput();

		Arg arg1 = arg;
		Arg arg2 = arg;

		String message = "Hello {0} {1}!";

		try (var gum = JDKSetup.run(output, loggerLevel)) {
			var logger = tester.logger("after.load");
			tester.twoArgs(logger, level, message, arg1, arg2);
			var levelString = LevelFormatter.of().formatLevel(level);
			String expected;
			if (isEnabled(level, loggerLevel)) {
				if (arg1 == Arg.BAD || arg2 == Arg.BAD) {
					expected = """
							00:00:00.000 [main] %s after.load - Hello {0} {1}! %s
							""".formatted(levelString, Arg.BAD.expected);
				}
				else {
					expected = """
							00:00:00.000 [main] %s after.load - Hello %s %s!
							""".formatted(levelString, arg1.expected, arg2.expected);
				}
			}
			else {
				expected = "";
			}
			String actual = output.toString();
			assertEquals(expected, actual);

		}

	}

	<T> void _testThreeArgs(LoggerTester<T> tester, System.Logger.Level level, System.Logger.Level loggerLevel,
			Arg arg) {
		ListLogOutput output = new ListLogOutput();

		Arg arg1 = arg;
		Arg arg2 = arg;
		Arg arg3 = arg;

		String message = "Hello {0} {1} {2}!";

		try (var gum = JDKSetup.run(output, loggerLevel)) {
			var logger = tester.logger("after.load");
			tester.threeArgs(logger, level, message, arg1, arg2, arg3);
			var levelString = LevelFormatter.of().formatLevel(level);
			String expected;
			if (isEnabled(level, loggerLevel)) {
				if (arg1 == Arg.BAD || arg2 == Arg.BAD) {
					expected = """
							00:00:00.000 [main] %s after.load - Hello {0} {1} {2}! %s
							""".formatted(levelString, Arg.BAD.expected);
				}
				else {
					expected = """
							00:00:00.000 [main] %s after.load - Hello %s %s %s!
							""".formatted(levelString, arg1.expected, arg2.expected, arg3.expected);
				}
			}
			else {
				expected = "";
			}
			String actual = output.toString();
			assertEquals(expected, actual);

		}

	}

	<T> void _testThrowable(LoggerTester<T> tester, System.Logger.Level level, System.Logger.Level loggerLevel) {
		ListLogOutput output = new ListLogOutput();

		String message = "Hello exception!";

		try (var gum = JDKSetup.run(output, loggerLevel)) {
			var logger = tester.logger("after.load");
			var throwable = new RuntimeException("expected");
			tester.throwable(logger, level, message, throwable);
			var levelString = LevelFormatter.of().formatLevel(level);
			String expected;
			if (isEnabled(level, loggerLevel)) {
				expected = """
						00:00:00.000 [main] %s after.load - Hello exception!
						java.lang.RuntimeException: expected""".formatted(levelString);
			}
			else {
				expected = "";
			}
			String actual = Stream.of(output.toString().split("\n", 3)).limit(2).collect(Collectors.joining("\n"));

			assertEquals(expected, actual);

		}

	}

	private static boolean isEnabled(System.Logger.Level level, System.Logger.Level loggerLevel) {
		if (level == System.Logger.Level.OFF) {
			return false;
		}
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

	private static Stream<Arguments> provideLevels() {
		List<Arguments> args = new ArrayList<>();
		for (var level : System.Logger.Level.values()) {
			for (var loggerLevel : System.Logger.Level.values()) {
				args.add(Arguments.of(level, loggerLevel));
			}
		}
		return args.stream();
	}

	private static Stream<Arguments> provideOneArg() {
		List<Arguments> args = new ArrayList<>();
		for (var level : System.Logger.Level.values()) {
			for (var loggerLevel : System.Logger.Level.values()) {
				for (var arg : Arg.values()) {
					args.add(Arguments.of(level, loggerLevel, arg));
				}
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

		assertNull(RainbowGum.getOrNull(), "Rainbow Gum should not be loaded yet.");
		System.setProperty(JULConfigurator.JUL_DISABLE_PROPERTY, "true");
		assertFalse(JULConfigurator.install(LogProperties.findGlobalProperties()));
		System.getProperties().remove(JULConfigurator.JUL_DISABLE_PROPERTY);
		assertFalse(JULConfigurator.isInstalled());

		var logger = System.getLogger("before.load");
		assertTrue(JULConfigurator.isInstalled());

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

	static final class BadToString {

		@Override
		public String toString() {
			throw new RuntimeException("expected");
		}

	}

	@SuppressWarnings("ImmutableEnumChecker")
	enum Arg {

		BAD("[MessageFormat failed: expected]", new BadToString()), //
		STRING("hello", "hello"), //
		INTEGER("1", 1), //
		;

		private final String expected;

		private final Object arg;

		private Arg(String expected, Object arg) {
			this.expected = expected;
			this.arg = arg;
		}

	}

}

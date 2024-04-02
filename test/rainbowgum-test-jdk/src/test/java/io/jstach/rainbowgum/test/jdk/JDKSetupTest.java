package io.jstach.rainbowgum.test.jdk;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;
import static org.junit.jupiter.api.Assumptions.abort;

import java.lang.System.Logger.Level;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Supplier;
import java.util.logging.Logger;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.eclipse.jdt.annotation.Nullable;
import org.junit.jupiter.api.MethodOrderer.OrderAnnotation;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;

import io.jstach.rainbowgum.LevelResolver;
import io.jstach.rainbowgum.LogFormatter.LevelFormatter;
import io.jstach.rainbowgum.LogProperties.MutableLogProperties;
import io.jstach.rainbowgum.LogProperties;
import io.jstach.rainbowgum.RainbowGum;
import io.jstach.rainbowgum.jdk.jul.JULConfigurator;
import io.jstach.rainbowgum.jdk.systemlogger.SystemLoggingFactory;
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
	@Test
	void testReuseFail() throws InterruptedException {
		try {
			System.setProperty(SystemLoggingFactory.INTIALIZE_RAINBOW_GUM_PROPERTY, "reuse");
			var e = assertThrows(IllegalStateException.class, () -> {
				new SystemLoggingFactory().getLogger("fail", null);
			});
			String actual = e.getMessage();
			String expected = "SystemLogging was configured to reuse a loaded Rainbow Gum but none was found. logging.systemlogger.intialize=REUSE";
			assertEquals(expected, actual);
		}
		finally {
			System.clearProperty(SystemLoggingFactory.INTIALIZE_RAINBOW_GUM_PROPERTY);
		}
	}

	@Order(4)
	@ParameterizedTest
	@MethodSource("provideLevels")
	void testName(LoggerTester<?> tester, System.Logger.Level level, System.Logger.Level loggerLevel)
			throws InterruptedException {
		doInLock(() -> {
			_testName(tester, level, loggerLevel);
		});

	}

	@Order(5)
	@ParameterizedTest
	@MethodSource("provideLevels")
	void testMessage(LoggerTester<?> tester, System.Logger.Level level, System.Logger.Level loggerLevel)
			throws InterruptedException {
		doInLock(() -> {
			_testMessage(tester, level, loggerLevel);
		});

	}

	@Order(6)
	@ParameterizedTest
	@MethodSource("provideLevels")
	void testMessageSupplier(LoggerTester<?> tester, System.Logger.Level level, System.Logger.Level loggerLevel)
			throws InterruptedException {
		doInLock(() -> {
			_testMessageSupplier(tester, level, loggerLevel);
		});
	}

	@Order(7)
	@ParameterizedTest
	@MethodSource("provideOneArg")
	void testObject(LoggerTester<?> tester, System.Logger.Level level, System.Logger.Level loggerLevel, Arg arg)
			throws InterruptedException {
		doInLock(() -> {
			_testObject(tester, level, loggerLevel, arg);
		});

	}

	@Order(8)
	@ParameterizedTest
	@MethodSource("provideOneArg")
	void testOneArg(LoggerTester<?> tester, System.Logger.Level level, System.Logger.Level loggerLevel, Arg arg)
			throws InterruptedException {
		doInLock(() -> {
			_testOneArg(tester, level, loggerLevel, arg);
		});

	}

	@Order(9)
	@ParameterizedTest
	@MethodSource("provideOneArg")
	void testTwoArgs(LoggerTester<?> tester, System.Logger.Level level, System.Logger.Level loggerLevel, Arg arg)
			throws InterruptedException {
		doInLock(() -> {
			_testTwoArgs(tester, level, loggerLevel, arg);
		});

	}

	@Order(10)
	@ParameterizedTest
	@MethodSource("provideOneArg")
	void testThreeArgs(LoggerTester<?> tester, System.Logger.Level level, System.Logger.Level loggerLevel, Arg arg)
			throws InterruptedException {
		doInLock(() -> {
			_testThreeArgs(tester, level, loggerLevel, arg);
		});

	}

	@Order(11)
	@ParameterizedTest
	@MethodSource("provideLevels")
	void testThrowable(LoggerTester<?> tester, System.Logger.Level level, System.Logger.Level loggerLevel)
			throws InterruptedException {
		doInLock(() -> {
			_testThrowable(tester, level, loggerLevel);
		});
	}

	@Order(12)
	@ParameterizedTest
	@EnumSource(System.Logger.Level.class)
	void testNullRecord(System.Logger.Level loggerLevel) throws InterruptedException {
		doInLock(() -> {
			ListLogOutput output = new ListLogOutput();
			var tester = JULLoggerTester.JUL;
			try (var gum = tester.run(output, loggerLevel)) {
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

		public String name(T logger, System.Logger.Level level);

		public void message(T logger, System.Logger.Level level, String message);

		public void messageSupplier(T logger, System.Logger.Level level, Supplier<String> message);

		public void object(T logger, System.Logger.Level level, Arg arg);

		public void oneArg(T logger, System.Logger.Level level, String message, Arg arg);

		public void twoArgs(T logger, System.Logger.Level level, String message, Arg arg1, Arg arg2);

		public void threeArgs(T logger, System.Logger.Level level, String message, Arg arg1, Arg arg2, Arg arg3);

		public void throwable(T logger, System.Logger.Level level, String message, Throwable throwable);

		default RainbowGum run(ListLogOutput output, System.Logger.Level level) {
			return JDKSetup.run(output, level, properties());
		}

		default LogProperties properties() {
			return LogProperties.StandardProperties.EMPTY;
		}

		default @Nullable T beforeLoad() {
			if (isAfterLoad()) {
				return null;
			}
			return logger("after.load");
		}

		default T afterLoad(@Nullable T logger) {
			if (logger == null) {
				return logger("after.load");
			}
			return logger;
		}

		default boolean isAfterLoad() {
			return false;
		}

	}

	enum JULLoggerTester implements LoggerTester<java.util.logging.Logger> {

		JUL;

		public java.util.logging.Logger logger(String loggerName) {
			return Logger.getLogger(loggerName);
		}

		@Override
		public String name(Logger logger, Level level) {
			return logger.getName();
		}

		public void message(java.util.logging.Logger logger, System.Logger.Level level, String message) {
			logger.log(julLevel(level), message);
		}

		@Override
		public void messageSupplier(Logger logger, Level level, Supplier<String> message) {
			logger.log(julLevel(level), message);

		}

		public void object(java.util.logging.Logger logger, System.Logger.Level level, Arg arg) {
			throw new UnsupportedOperationException();
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

		SYSTEM_LOGGER, //
		STATIC_GUM_SYSTEM_LOGGER() {
			@Override
			public java.lang.System.Logger logger(String loggerName) {
				return new SystemLoggingFactory().getLogger(loggerName, null);
			}

			@Override
			public boolean isAfterLoad() {
				return true;
			}

			@Override
			public LogProperties properties() {
				return MutableLogProperties.builder()
					.build()
					.put(SystemLoggingFactory.INTIALIZE_RAINBOW_GUM_PROPERTY, "true");
			}
		},
		REUSE_GUM_SYSTEM_LOGGER() {
			@Override
			public java.lang.System.Logger logger(String loggerName) {
				return new SystemLoggingFactory().getLogger(loggerName, null);
			}

			@Override
			public boolean isAfterLoad() {
				return true;
			}

			@Override
			public LogProperties properties() {
				return MutableLogProperties.builder()
					.build()
					.put(SystemLoggingFactory.INTIALIZE_RAINBOW_GUM_PROPERTY, "reuse");
			}
		},
		CHANGEABLE_GUM_SYSTEM_LOGGER() {
			@Override
			public java.lang.System.Logger logger(String loggerName) {
				return new SystemLoggingFactory().getLogger(loggerName, null);
			}

			@Override
			public boolean isAfterLoad() {
				return true;
			}

			@Override
			public LogProperties properties() {
				return MutableLogProperties.builder()
					.build()
					.put(LogProperties.GLOBAL_CHANGE_PROPERTY, "true")
					.put(LogProperties.CHANGE_PREFIX + "." + "after", "true")
					.put(SystemLoggingFactory.INTIALIZE_RAINBOW_GUM_PROPERTY, "true");
			}
		},;

		@Override
		public java.lang.System.Logger logger(String loggerName) {
			return System.getLogger(loggerName);
		}

		@Override
		public String name(java.lang.System.Logger logger, Level level) {
			return logger.getName();
		}

		@Override
		public void message(java.lang.System.Logger logger, Level level, String message) {
			logger.log(level, message);
		}

		@Override
		public void messageSupplier(java.lang.System.Logger logger, Level level, Supplier<String> message) {
			logger.log(level, message);
		}

		@Override
		public void object(java.lang.System.Logger logger, Level level, Arg message) {
			logger.log(level, message.arg);

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

	<T> void _testName(LoggerTester<T> tester, System.Logger.Level level, System.Logger.Level loggerLevel) {
		ListLogOutput output = new ListLogOutput();

		var logger = tester.beforeLoad();
		try (var gum = tester.run(output, loggerLevel)) {
			logger = tester.afterLoad(logger);
			String actual = tester.name(logger, loggerLevel);
			assertEquals("after.load", actual);
		}
	}

	<T> void _testMessage(LoggerTester<T> tester, System.Logger.Level level, System.Logger.Level loggerLevel) {
		ListLogOutput output = new ListLogOutput();
		String message = "Hello!";

		var logger = tester.beforeLoad();
		try (var gum = tester.run(output, loggerLevel)) {
			logger = tester.afterLoad(logger);
			tester.message(logger, level, message);
			var levelString = LevelFormatter.of().formatLevel(level);
			String expected;
			if (isEnabled(level, loggerLevel)) {
				expected = """
						00:00:00.000 [main] %s after.load - Hello!
						""".formatted(levelString);
			}
			else {
				expected = "";
			}
			String actual = output.toString();
			assertEquals(expected, actual);
		}

	}

	<T> void _testMessageSupplier(LoggerTester<T> tester, System.Logger.Level level, System.Logger.Level loggerLevel) {
		ListLogOutput output = new ListLogOutput();
		AtomicInteger count = new AtomicInteger();
		Supplier<String> message = () -> {
			count.incrementAndGet();
			return "Hello!";
		};

		var logger = tester.beforeLoad();
		try (var gum = tester.run(output, loggerLevel)) {
			logger = tester.afterLoad(logger);
			tester.messageSupplier(logger, level, message);
			var levelString = LevelFormatter.of().formatLevel(level);
			String expected;
			int expectedCount;
			if (isEnabled(level, loggerLevel)) {
				expectedCount = 1;
				expected = """
						00:00:00.000 [main] %s after.load - Hello!
						""".formatted(levelString);
			}
			else {
				expectedCount = 0;
				if (tester instanceof JULLoggerTester
						&& (level == System.Logger.Level.OFF && loggerLevel != System.Logger.Level.OFF)) {
					/*
					 * JUL is messed up on passing OFF as it will always create the record
					 */
					expectedCount = 1;
				}
				expected = "";
			}
			String actual = output.toString();
			assertEquals(expected, actual);
			assertEquals(expectedCount, count.get());
		}

	}

	<T> void _testObject(LoggerTester<T> tester, System.Logger.Level level, System.Logger.Level loggerLevel, Arg arg) {
		ListLogOutput output = new ListLogOutput();
		Object message = arg.arg;

		var logger = tester.beforeLoad();
		try (var gum = tester.run(output, loggerLevel)) {
			var _logger = logger = tester.afterLoad(logger);
			if (arg == Arg.BAD && isEnabled(level, loggerLevel)) {
				assertThrows(RuntimeException.class, () -> {
					tester.object(_logger, level, arg);
				});
				return;
			}
			else {
				tester.object(logger, level, arg);
			}
			var levelString = LevelFormatter.of().formatLevel(level);
			String expected;
			if (isEnabled(level, loggerLevel)) {
				expected = """
						00:00:00.000 [main] %s after.load - %s
						""".formatted(levelString, message);
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

		var logger = tester.beforeLoad();
		try (var gum = tester.run(output, loggerLevel)) {
			logger = tester.afterLoad(logger);
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

		var logger = tester.beforeLoad();
		try (var gum = tester.run(output, loggerLevel)) {
			logger = tester.afterLoad(logger);
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

		var logger = tester.beforeLoad();
		try (var gum = tester.run(output, loggerLevel)) {
			logger = tester.afterLoad(logger);
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

		var logger = tester.beforeLoad();
		try (var gum = tester.run(output, loggerLevel)) {
			logger = tester.afterLoad(logger);
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
		return LevelResolver.checkEnabled(level, loggerLevel);
	}

	private static List<LoggerTester<?>> provideTesters() {
		List<LoggerTester<?>> testers = new ArrayList<>();
		testers.addAll(EnumSet.allOf(JULLoggerTester.class));
		testers.addAll(EnumSet.allOf(SystemLoggerTester.class));
		return testers;

	}

	private static Stream<Arguments> provideLevels() {
		List<Arguments> args = new ArrayList<>();
		for (var tester : provideTesters()) {
			for (var level : System.Logger.Level.values()) {
				for (var loggerLevel : System.Logger.Level.values()) {
					args.add(Arguments.of(tester, level, loggerLevel));
				}
			}
		}
		return args.stream();
	}

	private static Stream<Arguments> provideOneArg() {
		List<Arguments> args = new ArrayList<>();
		for (var tester : provideTesters()) {
			for (var level : System.Logger.Level.values()) {
				for (var loggerLevel : System.Logger.Level.values()) {
					for (var arg : Arg.values()) {
						args.add(Arguments.of(tester, level, loggerLevel, arg));
					}
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
			catch (UnsupportedOperationException o) {
				abort("not supported");
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
		try (var gum = JDKSetup.run(output, System.Logger.Level.INFO, LogProperties.StandardProperties.EMPTY)) {
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

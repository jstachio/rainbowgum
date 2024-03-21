package io.jstach.rainbowgum.slf4j;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.fail;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.stream.Stream;

import org.eclipse.jdt.annotation.Nullable;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.slf4j.Logger;
import org.slf4j.Marker;
import org.slf4j.event.Level;
import org.slf4j.helpers.BasicMarkerFactory;

import io.jstach.rainbowgum.LogEventLogger;

public class LoggerMethodTest {

	String loggerName = "loggerName";

	RainbowGumMDCAdapter mdc = new RainbowGumMDCAdapter();

	StringBuilder output = new StringBuilder();

	LogEventLogger appender = e -> {
		e.formattedMessage(output);
	};

	@ParameterizedTest
	@MethodSource("provideParameters")
	void testLevelLogger(LoggerMethod method, Level level, @Nullable Level loggerLevel) {
		if (loggerLevel == null) {
			var logger = new LevelLogger.OffLogger(loggerName);
			method.test(level, logger);
			assertEquals("", output.toString());
			return;
		}
		var logger = LevelLogger.of(loggerLevel, loggerName, appender, mdc);
		String expected = method.test(level, logger);
		if (level.toInt() < loggerLevel.toInt()) {
			expected = "";
		}
		assertEquals(expected, output.toString());
	}

	@ParameterizedTest
	@MethodSource("provideParameters")
	void testChangeLoggerNoCallerInfo(LoggerMethod method, Level level, @Nullable Level loggerLevel) {
		if (loggerLevel == null) {
			return;
		}
		var logger = new ChangeableLogger(loggerName, appender, mdc, loggerLevel.toInt(), false);
		String expected = method.test(level, logger);
		if (level.toInt() < loggerLevel.toInt()) {
			expected = "";
		}
		assertEquals(expected, output.toString());
	}

	@ParameterizedTest
	@MethodSource("provideParameters")
	void testForwardingLogger(LoggerMethod method, Level level, @Nullable Level loggerLevel) {
		if (loggerLevel == null) {
			return;
		}
		var _logger = new ChangeableLogger(loggerName, appender, mdc, loggerLevel.toInt(), false);
		ForwardingLogger logger = new ForwardingLogger() {
			@Override
			public Logger delegate() {
				return _logger;
			}
		};

		String expected = method.test(level, logger);
		if (level.toInt() < loggerLevel.toInt()) {
			expected = "";
		}
		assertEquals(expected, output.toString());
	}

	sealed interface LoggerMethod {

		String test(Level level, Logger logger);

	}

	record LoggerMethodWrapper(MarkerLoggerMethod method) implements LoggerMethod {

		@Override
		public String test(Level level, Logger logger) {
			return method.test(level, logger, method.marker());
		}

		public String toString() {
			return "MARKER_" + method;
		}
	}

	sealed interface MarkerLoggerMethod extends LoggerMethod {

		String test(Level level, Logger logger, Marker marker);

		default Marker marker() {
			return new BasicMarkerFactory().getMarker("marker");
		}

		static <T extends Enum<T> & MarkerLoggerMethod> List<LoggerMethod> wrap(Class<T> type) {
			return EnumSet.allOf(type).stream().map(MarkerLoggerMethod::wrap).toList();
		}

		static LoggerMethod wrap(MarkerLoggerMethod m) {
			return new LoggerMethodWrapper(m);
		}

	}

	enum AT_LEVEL implements LoggerMethod {

		DEFAULT;

		@Override
		public String test(Level level, Logger logger) {
			logger.atLevel(level).log("true");
			if (logger.isEnabledForLevel(level)) {
				return "true";
			}
			return "";
		}

		@Override
		public String toString() {
			return this.getClass().getSimpleName();
		}

	}

	enum AT__level implements LoggerMethod {

		DEFAULT;

		@Override
		public String test(Level level, Logger logger) {
			var b = switch (level) {
				case DEBUG -> logger.atDebug();
				case ERROR -> logger.atError();
				case INFO -> logger.atInfo();
				case TRACE -> logger.atTrace();
				case WARN -> logger.atWarn();
			};
			b.log("true");
			if (logger.isEnabledForLevel(level)) {
				return "true";
			}
			return "";
		}

		@Override
		public String toString() {
			return this.getClass().getSimpleName();
		}

	}

	enum IS_ENABLED_FOR_LEVEL implements LoggerMethod {

		DEFAULT;

		@Override
		public String test(Level level, Logger logger) {
			if (logger.isEnabledForLevel(level)) {
				logger.makeLoggingEventBuilder(level).log("true");
				return "true";
			}
			return "";
		}

		@Override
		public String toString() {
			return this.getClass().getSimpleName();
		}

	}

	enum IS_Level_ENABLED implements MarkerLoggerMethod {

		DEFAULT;

		@Override
		public String test(Level level, Logger logger) {
			boolean result = switch (level) {
				case DEBUG -> logger.isDebugEnabled();
				case ERROR -> logger.isErrorEnabled();
				case INFO -> logger.isInfoEnabled();
				case TRACE -> logger.isTraceEnabled();
				case WARN -> logger.isWarnEnabled();
			};
			if (result) {
				logger.atLevel(level).log("true");
				return "true";
			}
			return "";

		}

		@Override
		public String toString() {
			return this.getClass().getSimpleName();
		}

		@Override
		public String test(Level level, Logger logger, Marker marker) {
			boolean result = switch (level) {
				case DEBUG -> logger.isDebugEnabled(marker);
				case ERROR -> logger.isErrorEnabled(marker);
				case INFO -> logger.isInfoEnabled(marker);
				case TRACE -> logger.isTraceEnabled(marker);
				case WARN -> logger.isWarnEnabled(marker);
			};
			if (result) {
				logger.atLevel(level).log("true");
				return "true";
			}
			return "";
		}

	}

	enum FormatMsg implements MarkerLoggerMethod, NoArg {

		DEFAULT;

		@Override
		public String test(Level level, Logger logger) {
			switch (level) {
				case DEBUG -> logger.debug(format());
				case ERROR -> logger.error(format());
				case INFO -> logger.info(format());
				case TRACE -> logger.trace(format());
				case WARN -> logger.warn(format());
			}
			return expected();
		}

		@Override
		public String test(Level level, Logger logger, Marker marker) {
			switch (level) {
				case DEBUG -> logger.debug(marker, format());
				case ERROR -> logger.error(marker, format());
				case INFO -> logger.info(marker, format());
				case TRACE -> logger.trace(marker, format());
				case WARN -> logger.warn(marker, format());
			}
			return expected();
		}

		@Override
		public String toString() {
			return this.getClass().getSimpleName() + "_" + name();
		}

	}

	enum FormatArg1 implements MarkerLoggerMethod, Arg1 {

		DEFAULT;

		@Override
		public String test(Level level, Logger logger) {
			switch (level) {
				case DEBUG -> logger.debug(format(), arg1());
				case ERROR -> logger.error(format(), arg1());
				case INFO -> logger.info(format(), arg1());
				case TRACE -> logger.trace(format(), arg1());
				case WARN -> logger.warn(format(), arg1());
			}
			return expected();
		}

		@Override
		public String test(Level level, Logger logger, Marker marker) {
			switch (level) {
				case DEBUG -> logger.debug(marker, format(), arg1());
				case ERROR -> logger.error(marker, format(), arg1());
				case INFO -> logger.info(marker, format(), arg1());
				case TRACE -> logger.trace(marker, format(), arg1());
				case WARN -> logger.warn(marker, format(), arg1());
			}
			return expected();
		}

		@Override
		public String toString() {
			return this.getClass().getSimpleName();
		}

	}

	enum FormatArg1Arg2 implements MarkerLoggerMethod, Arg1Arg2 {

		DEFAULT;

		@Override
		public String test(Level level, Logger logger) {
			switch (level) {
				case DEBUG -> logger.debug(format(), arg1(), arg2());
				case ERROR -> logger.error(format(), arg1(), arg2());
				case INFO -> logger.info(format(), arg1(), arg2());
				case TRACE -> logger.trace(format(), arg1(), arg2());
				case WARN -> logger.warn(format(), arg1(), arg2());
			}
			return expected();
		}

		@Override
		public String test(Level level, Logger logger, Marker marker) {
			switch (level) {
				case DEBUG -> logger.debug(marker, format(), arg1(), arg2());
				case ERROR -> logger.error(marker, format(), arg1(), arg2());
				case INFO -> logger.info(marker, format(), arg1(), arg2());
				case TRACE -> logger.trace(marker, format(), arg1(), arg2());
				case WARN -> logger.warn(marker, format(), arg1(), arg2());
			}
			return expected();
		}

		@Override
		public String toString() {
			return this.getClass().getSimpleName() + "_" + name();
		}

	}

	enum FormatArgArray implements MarkerLoggerMethod, ArgArray {

		DEFAULT;

		@Override
		public String test(Level level, Logger logger) {
			switch (level) {
				case DEBUG -> logger.debug(format(), args());
				case ERROR -> logger.error(format(), args());
				case INFO -> logger.info(format(), args());
				case TRACE -> logger.trace(format(), args());
				case WARN -> logger.warn(format(), args());
			}
			return expected();
		}

		@Override
		public String test(Level level, Logger logger, Marker marker) {
			switch (level) {
				case DEBUG -> logger.debug(marker, format(), args());
				case ERROR -> logger.error(marker, format(), args());
				case INFO -> logger.info(marker, format(), args());
				case TRACE -> logger.trace(marker, format(), args());
				case WARN -> logger.warn(marker, format(), args());
			}
			return expected();
		}

		@Override
		public String toString() {
			return this.getClass().getSimpleName() + "_" + name();
		}

	}

	enum FormatMessageThrowable implements MarkerLoggerMethod, NoArg {

		DEFAULT;

		public Throwable throwable() {
			return new RuntimeException("expected");
		}

		@Override
		public String test(Level level, Logger logger) {
			switch (level) {
				case DEBUG -> logger.debug(format(), throwable());
				case ERROR -> logger.error(format(), throwable());
				case INFO -> logger.info(format(), throwable());
				case TRACE -> logger.trace(format(), throwable());
				case WARN -> logger.warn(format(), throwable());
			}
			return expected();
		}

		@Override
		public String test(Level level, Logger logger, Marker marker) {
			switch (level) {
				case DEBUG -> logger.debug(marker, format(), throwable());
				case ERROR -> logger.error(marker, format(), throwable());
				case INFO -> logger.info(marker, format(), throwable());
				case TRACE -> logger.trace(marker, format(), throwable());
				case WARN -> logger.warn(marker, format(), throwable());
			}
			return expected();
		}

		@Override
		public String toString() {
			return this.getClass().getSimpleName();
		}

	}

	interface Format {

		String format();

		String expected();

	}

	interface NoArg extends Format {

		default String format() {
			return "Hello!";
		}

		default public String expected() {
			return "Hello!";
		}

	}

	interface ArgArray extends Format {

		default Object[] args() {
			return new Object[] { "arg1", "arg2", "arg3" };
		}

		default public String format() {
			return "Hello {} {} {}!";
		}

		default public String expected() {
			return "Hello arg1 arg2 arg3!";
		}

	}

	interface Arg1 extends Format {

		default Object arg1() {
			return "arg1";
		}

		default public String format() {
			return "Hello {}!";
		}

		default public String expected() {
			return "Hello arg1!";
		}

	}

	interface Arg1Arg2 extends Arg1 {

		default Object arg2() {
			return "arg2";
		}

		default public String format() {
			return "Hello {} {}!";
		}

		default public String expected() {
			return "Hello arg1 arg2!";
		}

	}

	private static Stream<Arguments> provideParameters() {
		List<Arguments> args = new ArrayList<>();
		for (var level : Level.values()) {
			for (var methodKind : MethodKind.values()) {
				var methods = methodKind.methods();
				for (var m : methods) {
					for (var loggerLevel : Level.values()) {
						args.add(Arguments.of(m, level, loggerLevel));
					}
					args.add(Arguments.of(m, level, null));

				}
			}

		}
		return args.stream();
	}

	record FailLoggerMethod(String kind) implements LoggerMethod {
		@Override
		public String test(Level level, Logger logger) {
			fail("Not tested yet");
			return "";
		}

	}

	enum MethodKind {

		AT_LEVEL(AT_LEVEL.class), //
		IS_ENABLED_FOR_LEVEL(IS_ENABLED_FOR_LEVEL.class), //
		IS_Level_ENABLED(IS_Level_ENABLED.class), //
		level_msg(FormatMsg.class), level_format_arg(FormatArg1.class), level_format_arg1_arg2(FormatArg1Arg2.class), //
		level_format_arguments(FormatArgArray.class), level_msg_t(FormatMessageThrowable.class), //
		at_level(AT__level.class), //
		level_marker_msg(MarkerLoggerMethod.wrap(FormatMsg.class)),
		level_marker_format_arg(MarkerLoggerMethod.wrap(FormatArg1.class)), //
		level_marker_format_arg1_arg2(MarkerLoggerMethod.wrap(FormatArg1Arg2.class)), //
		level_marker_format_argArray(MarkerLoggerMethod.wrap(FormatArgArray.class)), //
		level_marker_msg_t(MarkerLoggerMethod.wrap(FormatMessageThrowable.class)),
		is_level_enabled_marker(MarkerLoggerMethod.wrap(IS_Level_ENABLED.class));

		private final List<? extends LoggerMethod> methods;

		MethodKind() {
			this.methods = List.of(new FailLoggerMethod(name()));
		}

		private MethodKind(List<? extends LoggerMethod> methods) {
			this.methods = methods;
		}

		<T extends Enum<T> & LoggerMethod> MethodKind(Class<T> type) {
			this.methods = EnumSet.allOf(type).stream().toList();
		}

		List<? extends LoggerMethod> methods() {
			return methods;
		}

	}

}

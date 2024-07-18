package io.jstach.rainbowgum.slf4j;

import org.slf4j.Logger;
import org.slf4j.Marker;
import org.slf4j.event.Level;
import org.slf4j.spi.LoggingEventBuilder;
import org.slf4j.spi.NOPLoggingEventBuilder;

@SuppressWarnings("exports")
sealed interface LevelLogger extends BaseLogger, Logger {

	LogEventHandler handler();

	Level level();

	public LevelLogger withDepth(int depth);

	@Override
	default LoggingEventBuilder makeLoggingEventBuilder(Level level) {
		if (level().toInt() <= level.toInt()) {
			return handler().eventBuilder(level);
		}
		return NOPLoggingEventBuilder.singleton();
	}

	public static LevelLogger of(Level level, LogEventHandler handler) {
		return switch (level) {
			case ERROR -> new ErrorLogger(handler.loggerName(), handler);
			case WARN -> new WarnLogger(handler.loggerName(), handler);
			case INFO -> new InfoLogger(handler.loggerName(), handler);
			case DEBUG -> new DebugLogger(handler.loggerName(), handler);
			case TRACE -> new TraceLogger(handler.loggerName(), handler);
		};
	}

	record OffLogger(String loggerName) implements BaseLogger {
		@Override
		public LoggingEventBuilder makeLoggingEventBuilder(Level level) {
			return NOPLoggingEventBuilder.singleton();
		}

		@Override
		public Logger withDepth(int depth) {
			return this;
		}

	}

	record ErrorLogger(String loggerName, LogEventHandler handler) implements LevelLogger {

		@Override
		public Level level() {
			return Level.ERROR;
		}

		@Override
		public LevelLogger withDepth(int depth) {
			return new ErrorLogger(loggerName, handler.withDepth(depth));
		}

		@Override
		public LoggingEventBuilder atError() {
			return makeLoggingEventBuilder(Level.ERROR);
		}

		@Override
		public boolean isErrorEnabled() {
			return true;
		}

		@Override
		public void error(String msg) {
			handler().handle(Level.ERROR, msg);
		}

		@Override
		public void error(String format, Object arg) {
			handler().handle(Level.ERROR, format, arg);
		}

		@Override
		public void error(String format, Object arg1, Object arg2) {
			handler().handle(Level.ERROR, format, arg1, arg2);
		}

		@Override
		public void error(String format, Object... arguments) {
			handler().handleArray(Level.ERROR, format, arguments);
		}

		@Override
		public void error(String msg, Throwable t) {
			handler().handle(Level.ERROR, msg, t);
		}

		@Override
		public boolean isErrorEnabled(Marker marker) {
			return true;
		}

		@Override
		public void error(Marker marker, String msg) {
			handler().handle(Level.ERROR, msg);
		}

		@Override
		public void error(Marker marker, String format, Object arg) {
			handler().handle(Level.ERROR, format, arg);
		}

		@Override
		public void error(Marker marker, String format, Object arg1, Object arg2) {
			handler().handle(Level.ERROR, format, arg1, arg2);
		}

		@Override
		public void error(Marker marker, String format, Object... argArray) {
			handler().handleArray(Level.ERROR, format, argArray);
		}

		@Override
		public void error(Marker marker, String msg, Throwable t) {
			handler().handle(Level.ERROR, msg, t);
		}

		@Override
		public boolean isWarnEnabled() {
			return false;
		}

		@Override
		public void warn(String msg) {
		}

		@Override
		public void warn(String format, Object arg) {
		}

		@Override
		public void warn(String format, Object arg1, Object arg2) {
		}

		@Override
		public void warn(String format, Object... arguments) {
		}

		@Override
		public void warn(String msg, Throwable t) {
		}

		@Override
		public boolean isWarnEnabled(Marker marker) {
			return false;
		}

		@Override
		public void warn(Marker marker, String msg) {
		}

		@Override
		public void warn(Marker marker, String format, Object arg) {
		}

		@Override
		public void warn(Marker marker, String format, Object arg1, Object arg2) {
		}

		@Override
		public void warn(Marker marker, String format, Object... argArray) {
		}

		@Override
		public void warn(Marker marker, String msg, Throwable t) {
		}

		@Override
		public boolean isInfoEnabled() {
			return false;
		}

		@Override
		public void info(String msg) {
		}

		@Override
		public void info(String format, Object arg) {
		}

		@Override
		public void info(String format, Object arg1, Object arg2) {
		}

		@Override
		public void info(String format, Object... arguments) {
		}

		@Override
		public void info(String msg, Throwable t) {
		}

		@Override
		public boolean isInfoEnabled(Marker marker) {
			return false;
		}

		@Override
		public void info(Marker marker, String msg) {
		}

		@Override
		public void info(Marker marker, String format, Object arg) {
		}

		@Override
		public void info(Marker marker, String format, Object arg1, Object arg2) {
		}

		@Override
		public void info(Marker marker, String format, Object... argArray) {
		}

		@Override
		public void info(Marker marker, String msg, Throwable t) {
		}

		@Override
		public boolean isDebugEnabled() {
			return false;
		}

		@Override
		public void debug(String msg) {
		}

		@Override
		public void debug(String format, Object arg) {
		}

		@Override
		public void debug(String format, Object arg1, Object arg2) {
		}

		@Override
		public void debug(String format, Object... arguments) {
		}

		@Override
		public void debug(String msg, Throwable t) {
		}

		@Override
		public boolean isDebugEnabled(Marker marker) {
			return false;
		}

		@Override
		public void debug(Marker marker, String msg) {
		}

		@Override
		public void debug(Marker marker, String format, Object arg) {
		}

		@Override
		public void debug(Marker marker, String format, Object arg1, Object arg2) {
		}

		@Override
		public void debug(Marker marker, String format, Object... argArray) {
		}

		@Override
		public void debug(Marker marker, String msg, Throwable t) {
		}

		@Override
		public boolean isTraceEnabled() {
			return false;
		}

		@Override
		public void trace(String msg) {
		}

		@Override
		public void trace(String format, Object arg) {
		}

		@Override
		public void trace(String format, Object arg1, Object arg2) {
		}

		@Override
		public void trace(String format, Object... arguments) {
		}

		@Override
		public void trace(String msg, Throwable t) {
		}

		@Override
		public boolean isTraceEnabled(Marker marker) {
			return false;
		}

		@Override
		public void trace(Marker marker, String msg) {
		}

		@Override
		public void trace(Marker marker, String format, Object arg) {
		}

		@Override
		public void trace(Marker marker, String format, Object arg1, Object arg2) {
		}

		@Override
		public void trace(Marker marker, String format, Object... argArray) {
		}

		@Override
		public void trace(Marker marker, String msg, Throwable t) {
		}
	}

	record WarnLogger(String loggerName, LogEventHandler handler) implements LevelLogger {

		@Override
		public Level level() {
			return Level.WARN;
		}

		@Override
		public LevelLogger withDepth(int depth) {
			return new WarnLogger(loggerName, handler.withDepth(depth));
		}

		@Override
		public LoggingEventBuilder atError() {
			return makeLoggingEventBuilder(Level.ERROR);
		}

		@Override
		public boolean isErrorEnabled() {
			return true;
		}

		@Override
		public void error(String msg) {
			handler().handle(Level.ERROR, msg);
		}

		@Override
		public void error(String format, Object arg) {
			handler().handle(Level.ERROR, format, arg);
		}

		@Override
		public void error(String format, Object arg1, Object arg2) {
			handler().handle(Level.ERROR, format, arg1, arg2);
		}

		@Override
		public void error(String format, Object... arguments) {
			handler().handleArray(Level.ERROR, format, arguments);
		}

		@Override
		public void error(String msg, Throwable t) {
			handler().handle(Level.ERROR, msg, t);
		}

		@Override
		public boolean isErrorEnabled(Marker marker) {
			return true;
		}

		@Override
		public void error(Marker marker, String msg) {
			handler().handle(Level.ERROR, msg);
		}

		@Override
		public void error(Marker marker, String format, Object arg) {
			handler().handle(Level.ERROR, format, arg);
		}

		@Override
		public void error(Marker marker, String format, Object arg1, Object arg2) {
			handler().handle(Level.ERROR, format, arg1, arg2);
		}

		@Override
		public void error(Marker marker, String format, Object... argArray) {
			handler().handleArray(Level.ERROR, format, argArray);
		}

		@Override
		public void error(Marker marker, String msg, Throwable t) {
			handler().handle(Level.ERROR, msg, t);
		}

		@Override
		public LoggingEventBuilder atWarn() {
			return makeLoggingEventBuilder(Level.WARN);
		}

		@Override
		public boolean isWarnEnabled() {
			return true;
		}

		@Override
		public void warn(String msg) {
			handler().handle(Level.WARN, msg);
		}

		@Override
		public void warn(String format, Object arg) {
			handler().handle(Level.WARN, format, arg);
		}

		@Override
		public void warn(String format, Object arg1, Object arg2) {
			handler().handle(Level.WARN, format, arg1, arg2);
		}

		@Override
		public void warn(String format, Object... arguments) {
			handler().handleArray(Level.WARN, format, arguments);
		}

		@Override
		public void warn(String msg, Throwable t) {
			handler().handle(Level.WARN, msg, t);
		}

		@Override
		public boolean isWarnEnabled(Marker marker) {
			return true;
		}

		@Override
		public void warn(Marker marker, String msg) {
			handler().handle(Level.WARN, msg);
		}

		@Override
		public void warn(Marker marker, String format, Object arg) {
			handler().handle(Level.WARN, format, arg);
		}

		@Override
		public void warn(Marker marker, String format, Object arg1, Object arg2) {
			handler().handle(Level.WARN, format, arg1, arg2);
		}

		@Override
		public void warn(Marker marker, String format, Object... argArray) {
			handler().handleArray(Level.WARN, format, argArray);
		}

		@Override
		public void warn(Marker marker, String msg, Throwable t) {
			handler().handle(Level.WARN, msg, t);
		}

		@Override
		public boolean isInfoEnabled() {
			return false;
		}

		@Override
		public void info(String msg) {
		}

		@Override
		public void info(String format, Object arg) {
		}

		@Override
		public void info(String format, Object arg1, Object arg2) {
		}

		@Override
		public void info(String format, Object... arguments) {
		}

		@Override
		public void info(String msg, Throwable t) {
		}

		@Override
		public boolean isInfoEnabled(Marker marker) {
			return false;
		}

		@Override
		public void info(Marker marker, String msg) {
		}

		@Override
		public void info(Marker marker, String format, Object arg) {
		}

		@Override
		public void info(Marker marker, String format, Object arg1, Object arg2) {
		}

		@Override
		public void info(Marker marker, String format, Object... argArray) {
		}

		@Override
		public void info(Marker marker, String msg, Throwable t) {
		}

		@Override
		public boolean isDebugEnabled() {
			return false;
		}

		@Override
		public void debug(String msg) {
		}

		@Override
		public void debug(String format, Object arg) {
		}

		@Override
		public void debug(String format, Object arg1, Object arg2) {
		}

		@Override
		public void debug(String format, Object... arguments) {
		}

		@Override
		public void debug(String msg, Throwable t) {
		}

		@Override
		public boolean isDebugEnabled(Marker marker) {
			return false;
		}

		@Override
		public void debug(Marker marker, String msg) {
		}

		@Override
		public void debug(Marker marker, String format, Object arg) {
		}

		@Override
		public void debug(Marker marker, String format, Object arg1, Object arg2) {
		}

		@Override
		public void debug(Marker marker, String format, Object... argArray) {
		}

		@Override
		public void debug(Marker marker, String msg, Throwable t) {
		}

		@Override
		public boolean isTraceEnabled() {
			return false;
		}

		@Override
		public void trace(String msg) {
		}

		@Override
		public void trace(String format, Object arg) {
		}

		@Override
		public void trace(String format, Object arg1, Object arg2) {
		}

		@Override
		public void trace(String format, Object... arguments) {
		}

		@Override
		public void trace(String msg, Throwable t) {
		}

		@Override
		public boolean isTraceEnabled(Marker marker) {
			return false;
		}

		@Override
		public void trace(Marker marker, String msg) {
		}

		@Override
		public void trace(Marker marker, String format, Object arg) {
		}

		@Override
		public void trace(Marker marker, String format, Object arg1, Object arg2) {
		}

		@Override
		public void trace(Marker marker, String format, Object... argArray) {
		}

		@Override
		public void trace(Marker marker, String msg, Throwable t) {
		}
	}

	record InfoLogger(String loggerName, LogEventHandler handler) implements LevelLogger {

		@Override
		public Level level() {
			return Level.INFO;
		}

		@Override
		public LevelLogger withDepth(int depth) {
			return new InfoLogger(loggerName, handler.withDepth(depth));
		}

		@Override
		public LoggingEventBuilder atError() {
			return makeLoggingEventBuilder(Level.ERROR);
		}

		@Override
		public boolean isErrorEnabled() {
			return true;
		}

		@Override
		public void error(String msg) {
			handler().handle(Level.ERROR, msg);
		}

		@Override
		public void error(String format, Object arg) {
			handler().handle(Level.ERROR, format, arg);
		}

		@Override
		public void error(String format, Object arg1, Object arg2) {
			handler().handle(Level.ERROR, format, arg1, arg2);
		}

		@Override
		public void error(String format, Object... arguments) {
			handler().handleArray(Level.ERROR, format, arguments);
		}

		@Override
		public void error(String msg, Throwable t) {
			handler().handle(Level.ERROR, msg, t);
		}

		@Override
		public boolean isErrorEnabled(Marker marker) {
			return true;
		}

		@Override
		public void error(Marker marker, String msg) {
			handler().handle(Level.ERROR, msg);
		}

		@Override
		public void error(Marker marker, String format, Object arg) {
			handler().handle(Level.ERROR, format, arg);
		}

		@Override
		public void error(Marker marker, String format, Object arg1, Object arg2) {
			handler().handle(Level.ERROR, format, arg1, arg2);
		}

		@Override
		public void error(Marker marker, String format, Object... argArray) {
			handler().handleArray(Level.ERROR, format, argArray);
		}

		@Override
		public void error(Marker marker, String msg, Throwable t) {
			handler().handle(Level.ERROR, msg, t);
		}

		@Override
		public LoggingEventBuilder atWarn() {
			return makeLoggingEventBuilder(Level.WARN);
		}

		@Override
		public boolean isWarnEnabled() {
			return true;
		}

		@Override
		public void warn(String msg) {
			handler().handle(Level.WARN, msg);
		}

		@Override
		public void warn(String format, Object arg) {
			handler().handle(Level.WARN, format, arg);
		}

		@Override
		public void warn(String format, Object arg1, Object arg2) {
			handler().handle(Level.WARN, format, arg1, arg2);
		}

		@Override
		public void warn(String format, Object... arguments) {
			handler().handleArray(Level.WARN, format, arguments);
		}

		@Override
		public void warn(String msg, Throwable t) {
			handler().handle(Level.WARN, msg, t);
		}

		@Override
		public boolean isWarnEnabled(Marker marker) {
			return true;
		}

		@Override
		public void warn(Marker marker, String msg) {
			handler().handle(Level.WARN, msg);
		}

		@Override
		public void warn(Marker marker, String format, Object arg) {
			handler().handle(Level.WARN, format, arg);
		}

		@Override
		public void warn(Marker marker, String format, Object arg1, Object arg2) {
			handler().handle(Level.WARN, format, arg1, arg2);
		}

		@Override
		public void warn(Marker marker, String format, Object... argArray) {
			handler().handleArray(Level.WARN, format, argArray);
		}

		@Override
		public void warn(Marker marker, String msg, Throwable t) {
			handler().handle(Level.WARN, msg, t);
		}

		@Override
		public LoggingEventBuilder atInfo() {
			return makeLoggingEventBuilder(Level.INFO);
		}

		@Override
		public boolean isInfoEnabled() {
			return true;
		}

		@Override
		public void info(String msg) {
			handler().handle(Level.INFO, msg);
		}

		@Override
		public void info(String format, Object arg) {
			handler().handle(Level.INFO, format, arg);
		}

		@Override
		public void info(String format, Object arg1, Object arg2) {
			handler().handle(Level.INFO, format, arg1, arg2);
		}

		@Override
		public void info(String format, Object... arguments) {
			handler().handleArray(Level.INFO, format, arguments);
		}

		@Override
		public void info(String msg, Throwable t) {
			handler().handle(Level.INFO, msg, t);
		}

		@Override
		public boolean isInfoEnabled(Marker marker) {
			return true;
		}

		@Override
		public void info(Marker marker, String msg) {
			handler().handle(Level.INFO, msg);
		}

		@Override
		public void info(Marker marker, String format, Object arg) {
			handler().handle(Level.INFO, format, arg);
		}

		@Override
		public void info(Marker marker, String format, Object arg1, Object arg2) {
			handler().handle(Level.INFO, format, arg1, arg2);
		}

		@Override
		public void info(Marker marker, String format, Object... argArray) {
			handler().handleArray(Level.INFO, format, argArray);
		}

		@Override
		public void info(Marker marker, String msg, Throwable t) {
			handler().handle(Level.INFO, msg, t);
		}

		@Override
		public boolean isDebugEnabled() {
			return false;
		}

		@Override
		public void debug(String msg) {
		}

		@Override
		public void debug(String format, Object arg) {
		}

		@Override
		public void debug(String format, Object arg1, Object arg2) {
		}

		@Override
		public void debug(String format, Object... arguments) {
		}

		@Override
		public void debug(String msg, Throwable t) {
		}

		@Override
		public boolean isDebugEnabled(Marker marker) {
			return false;
		}

		@Override
		public void debug(Marker marker, String msg) {
		}

		@Override
		public void debug(Marker marker, String format, Object arg) {
		}

		@Override
		public void debug(Marker marker, String format, Object arg1, Object arg2) {
		}

		@Override
		public void debug(Marker marker, String format, Object... argArray) {
		}

		@Override
		public void debug(Marker marker, String msg, Throwable t) {
		}

		@Override
		public boolean isTraceEnabled() {
			return false;
		}

		@Override
		public void trace(String msg) {
		}

		@Override
		public void trace(String format, Object arg) {
		}

		@Override
		public void trace(String format, Object arg1, Object arg2) {
		}

		@Override
		public void trace(String format, Object... arguments) {
		}

		@Override
		public void trace(String msg, Throwable t) {
		}

		@Override
		public boolean isTraceEnabled(Marker marker) {
			return false;
		}

		@Override
		public void trace(Marker marker, String msg) {
		}

		@Override
		public void trace(Marker marker, String format, Object arg) {
		}

		@Override
		public void trace(Marker marker, String format, Object arg1, Object arg2) {
		}

		@Override
		public void trace(Marker marker, String format, Object... argArray) {
		}

		@Override
		public void trace(Marker marker, String msg, Throwable t) {
		}
	}

	record DebugLogger(String loggerName, LogEventHandler handler) implements LevelLogger {

		@Override
		public Level level() {
			return Level.DEBUG;
		}

		@Override
		public LevelLogger withDepth(int depth) {
			return new DebugLogger(loggerName, handler.withDepth(depth));
		}

		@Override
		public LoggingEventBuilder atError() {
			return makeLoggingEventBuilder(Level.ERROR);
		}

		@Override
		public boolean isErrorEnabled() {
			return true;
		}

		@Override
		public void error(String msg) {
			handler().handle(Level.ERROR, msg);
		}

		@Override
		public void error(String format, Object arg) {
			handler().handle(Level.ERROR, format, arg);
		}

		@Override
		public void error(String format, Object arg1, Object arg2) {
			handler().handle(Level.ERROR, format, arg1, arg2);
		}

		@Override
		public void error(String format, Object... arguments) {
			handler().handleArray(Level.ERROR, format, arguments);
		}

		@Override
		public void error(String msg, Throwable t) {
			handler().handle(Level.ERROR, msg, t);
		}

		@Override
		public boolean isErrorEnabled(Marker marker) {
			return true;
		}

		@Override
		public void error(Marker marker, String msg) {
			handler().handle(Level.ERROR, msg);
		}

		@Override
		public void error(Marker marker, String format, Object arg) {
			handler().handle(Level.ERROR, format, arg);
		}

		@Override
		public void error(Marker marker, String format, Object arg1, Object arg2) {
			handler().handle(Level.ERROR, format, arg1, arg2);
		}

		@Override
		public void error(Marker marker, String format, Object... argArray) {
			handler().handleArray(Level.ERROR, format, argArray);
		}

		@Override
		public void error(Marker marker, String msg, Throwable t) {
			handler().handle(Level.ERROR, msg, t);
		}

		@Override
		public LoggingEventBuilder atWarn() {
			return makeLoggingEventBuilder(Level.WARN);
		}

		@Override
		public boolean isWarnEnabled() {
			return true;
		}

		@Override
		public void warn(String msg) {
			handler().handle(Level.WARN, msg);
		}

		@Override
		public void warn(String format, Object arg) {
			handler().handle(Level.WARN, format, arg);
		}

		@Override
		public void warn(String format, Object arg1, Object arg2) {
			handler().handle(Level.WARN, format, arg1, arg2);
		}

		@Override
		public void warn(String format, Object... arguments) {
			handler().handleArray(Level.WARN, format, arguments);
		}

		@Override
		public void warn(String msg, Throwable t) {
			handler().handle(Level.WARN, msg, t);
		}

		@Override
		public boolean isWarnEnabled(Marker marker) {
			return true;
		}

		@Override
		public void warn(Marker marker, String msg) {
			handler().handle(Level.WARN, msg);
		}

		@Override
		public void warn(Marker marker, String format, Object arg) {
			handler().handle(Level.WARN, format, arg);
		}

		@Override
		public void warn(Marker marker, String format, Object arg1, Object arg2) {
			handler().handle(Level.WARN, format, arg1, arg2);
		}

		@Override
		public void warn(Marker marker, String format, Object... argArray) {
			handler().handleArray(Level.WARN, format, argArray);
		}

		@Override
		public void warn(Marker marker, String msg, Throwable t) {
			handler().handle(Level.WARN, msg, t);
		}

		@Override
		public LoggingEventBuilder atInfo() {
			return makeLoggingEventBuilder(Level.INFO);
		}

		@Override
		public boolean isInfoEnabled() {
			return true;
		}

		@Override
		public void info(String msg) {
			handler().handle(Level.INFO, msg);
		}

		@Override
		public void info(String format, Object arg) {
			handler().handle(Level.INFO, format, arg);
		}

		@Override
		public void info(String format, Object arg1, Object arg2) {
			handler().handle(Level.INFO, format, arg1, arg2);
		}

		@Override
		public void info(String format, Object... arguments) {
			handler().handleArray(Level.INFO, format, arguments);
		}

		@Override
		public void info(String msg, Throwable t) {
			handler().handle(Level.INFO, msg, t);
		}

		@Override
		public boolean isInfoEnabled(Marker marker) {
			return true;
		}

		@Override
		public void info(Marker marker, String msg) {
			handler().handle(Level.INFO, msg);
		}

		@Override
		public void info(Marker marker, String format, Object arg) {
			handler().handle(Level.INFO, format, arg);
		}

		@Override
		public void info(Marker marker, String format, Object arg1, Object arg2) {
			handler().handle(Level.INFO, format, arg1, arg2);
		}

		@Override
		public void info(Marker marker, String format, Object... argArray) {
			handler().handleArray(Level.INFO, format, argArray);
		}

		@Override
		public void info(Marker marker, String msg, Throwable t) {
			handler().handle(Level.INFO, msg, t);
		}

		@Override
		public LoggingEventBuilder atDebug() {
			return makeLoggingEventBuilder(Level.DEBUG);
		}

		@Override
		public boolean isDebugEnabled() {
			return true;
		}

		@Override
		public void debug(String msg) {
			handler().handle(Level.DEBUG, msg);
		}

		@Override
		public void debug(String format, Object arg) {
			handler().handle(Level.DEBUG, format, arg);
		}

		@Override
		public void debug(String format, Object arg1, Object arg2) {
			handler().handle(Level.DEBUG, format, arg1, arg2);
		}

		@Override
		public void debug(String format, Object... arguments) {
			handler().handleArray(Level.DEBUG, format, arguments);
		}

		@Override
		public void debug(String msg, Throwable t) {
			handler().handle(Level.DEBUG, msg, t);
		}

		@Override
		public boolean isDebugEnabled(Marker marker) {
			return true;
		}

		@Override
		public void debug(Marker marker, String msg) {
			handler().handle(Level.DEBUG, msg);
		}

		@Override
		public void debug(Marker marker, String format, Object arg) {
			handler().handle(Level.DEBUG, format, arg);
		}

		@Override
		public void debug(Marker marker, String format, Object arg1, Object arg2) {
			handler().handle(Level.DEBUG, format, arg1, arg2);
		}

		@Override
		public void debug(Marker marker, String format, Object... argArray) {
			handler().handleArray(Level.DEBUG, format, argArray);
		}

		@Override
		public void debug(Marker marker, String msg, Throwable t) {
			handler().handle(Level.DEBUG, msg, t);
		}

		@Override
		public boolean isTraceEnabled() {
			return false;
		}

		@Override
		public void trace(String msg) {
		}

		@Override
		public void trace(String format, Object arg) {
		}

		@Override
		public void trace(String format, Object arg1, Object arg2) {
		}

		@Override
		public void trace(String format, Object... arguments) {
		}

		@Override
		public void trace(String msg, Throwable t) {
		}

		@Override
		public boolean isTraceEnabled(Marker marker) {
			return false;
		}

		@Override
		public void trace(Marker marker, String msg) {
		}

		@Override
		public void trace(Marker marker, String format, Object arg) {
		}

		@Override
		public void trace(Marker marker, String format, Object arg1, Object arg2) {
		}

		@Override
		public void trace(Marker marker, String format, Object... argArray) {
		}

		@Override
		public void trace(Marker marker, String msg, Throwable t) {
		}
	}

	record TraceLogger(String loggerName, LogEventHandler handler) implements LevelLogger {

		@Override
		public Level level() {
			return Level.TRACE;
		}

		@Override
		public LevelLogger withDepth(int depth) {
			return new TraceLogger(loggerName, handler.withDepth(depth));
		}

		@Override
		public LoggingEventBuilder atError() {
			return makeLoggingEventBuilder(Level.ERROR);
		}

		@Override
		public boolean isErrorEnabled() {
			return true;
		}

		@Override
		public void error(String msg) {
			handler().handle(Level.ERROR, msg);
		}

		@Override
		public void error(String format, Object arg) {
			handler().handle(Level.ERROR, format, arg);
		}

		@Override
		public void error(String format, Object arg1, Object arg2) {
			handler().handle(Level.ERROR, format, arg1, arg2);
		}

		@Override
		public void error(String format, Object... arguments) {
			handler().handleArray(Level.ERROR, format, arguments);
		}

		@Override
		public void error(String msg, Throwable t) {
			handler().handle(Level.ERROR, msg, t);
		}

		@Override
		public boolean isErrorEnabled(Marker marker) {
			return true;
		}

		@Override
		public void error(Marker marker, String msg) {
			handler().handle(Level.ERROR, msg);
		}

		@Override
		public void error(Marker marker, String format, Object arg) {
			handler().handle(Level.ERROR, format, arg);
		}

		@Override
		public void error(Marker marker, String format, Object arg1, Object arg2) {
			handler().handle(Level.ERROR, format, arg1, arg2);
		}

		@Override
		public void error(Marker marker, String format, Object... argArray) {
			handler().handleArray(Level.ERROR, format, argArray);
		}

		@Override
		public void error(Marker marker, String msg, Throwable t) {
			handler().handle(Level.ERROR, msg, t);
		}

		@Override
		public LoggingEventBuilder atWarn() {
			return makeLoggingEventBuilder(Level.WARN);
		}

		@Override
		public boolean isWarnEnabled() {
			return true;
		}

		@Override
		public void warn(String msg) {
			handler().handle(Level.WARN, msg);
		}

		@Override
		public void warn(String format, Object arg) {
			handler().handle(Level.WARN, format, arg);
		}

		@Override
		public void warn(String format, Object arg1, Object arg2) {
			handler().handle(Level.WARN, format, arg1, arg2);
		}

		@Override
		public void warn(String format, Object... arguments) {
			handler().handleArray(Level.WARN, format, arguments);
		}

		@Override
		public void warn(String msg, Throwable t) {
			handler().handle(Level.WARN, msg, t);
		}

		@Override
		public boolean isWarnEnabled(Marker marker) {
			return true;
		}

		@Override
		public void warn(Marker marker, String msg) {
			handler().handle(Level.WARN, msg);
		}

		@Override
		public void warn(Marker marker, String format, Object arg) {
			handler().handle(Level.WARN, format, arg);
		}

		@Override
		public void warn(Marker marker, String format, Object arg1, Object arg2) {
			handler().handle(Level.WARN, format, arg1, arg2);
		}

		@Override
		public void warn(Marker marker, String format, Object... argArray) {
			handler().handleArray(Level.WARN, format, argArray);
		}

		@Override
		public void warn(Marker marker, String msg, Throwable t) {
			handler().handle(Level.WARN, msg, t);
		}

		@Override
		public LoggingEventBuilder atInfo() {
			return makeLoggingEventBuilder(Level.INFO);
		}

		@Override
		public boolean isInfoEnabled() {
			return true;
		}

		@Override
		public void info(String msg) {
			handler().handle(Level.INFO, msg);
		}

		@Override
		public void info(String format, Object arg) {
			handler().handle(Level.INFO, format, arg);
		}

		@Override
		public void info(String format, Object arg1, Object arg2) {
			handler().handle(Level.INFO, format, arg1, arg2);
		}

		@Override
		public void info(String format, Object... arguments) {
			handler().handleArray(Level.INFO, format, arguments);
		}

		@Override
		public void info(String msg, Throwable t) {
			handler().handle(Level.INFO, msg, t);
		}

		@Override
		public boolean isInfoEnabled(Marker marker) {
			return true;
		}

		@Override
		public void info(Marker marker, String msg) {
			handler().handle(Level.INFO, msg);
		}

		@Override
		public void info(Marker marker, String format, Object arg) {
			handler().handle(Level.INFO, format, arg);
		}

		@Override
		public void info(Marker marker, String format, Object arg1, Object arg2) {
			handler().handle(Level.INFO, format, arg1, arg2);
		}

		@Override
		public void info(Marker marker, String format, Object... argArray) {
			handler().handleArray(Level.INFO, format, argArray);
		}

		@Override
		public void info(Marker marker, String msg, Throwable t) {
			handler().handle(Level.INFO, msg, t);
		}

		@Override
		public LoggingEventBuilder atDebug() {
			return makeLoggingEventBuilder(Level.DEBUG);
		}

		@Override
		public boolean isDebugEnabled() {
			return true;
		}

		@Override
		public void debug(String msg) {
			handler().handle(Level.DEBUG, msg);
		}

		@Override
		public void debug(String format, Object arg) {
			handler().handle(Level.DEBUG, format, arg);
		}

		@Override
		public void debug(String format, Object arg1, Object arg2) {
			handler().handle(Level.DEBUG, format, arg1, arg2);
		}

		@Override
		public void debug(String format, Object... arguments) {
			handler().handleArray(Level.DEBUG, format, arguments);
		}

		@Override
		public void debug(String msg, Throwable t) {
			handler().handle(Level.DEBUG, msg, t);
		}

		@Override
		public boolean isDebugEnabled(Marker marker) {
			return true;
		}

		@Override
		public void debug(Marker marker, String msg) {
			handler().handle(Level.DEBUG, msg);
		}

		@Override
		public void debug(Marker marker, String format, Object arg) {
			handler().handle(Level.DEBUG, format, arg);
		}

		@Override
		public void debug(Marker marker, String format, Object arg1, Object arg2) {
			handler().handle(Level.DEBUG, format, arg1, arg2);
		}

		@Override
		public void debug(Marker marker, String format, Object... argArray) {
			handler().handleArray(Level.DEBUG, format, argArray);
		}

		@Override
		public void debug(Marker marker, String msg, Throwable t) {
			handler().handle(Level.DEBUG, msg, t);
		}

		@Override
		public LoggingEventBuilder atTrace() {
			return makeLoggingEventBuilder(Level.TRACE);
		}

		@Override
		public boolean isTraceEnabled() {
			return true;
		}

		@Override
		public void trace(String msg) {
			handler().handle(Level.TRACE, msg);
		}

		@Override
		public void trace(String format, Object arg) {
			handler().handle(Level.TRACE, format, arg);
		}

		@Override
		public void trace(String format, Object arg1, Object arg2) {
			handler().handle(Level.TRACE, format, arg1, arg2);
		}

		@Override
		public void trace(String format, Object... arguments) {
			handler().handleArray(Level.TRACE, format, arguments);
		}

		@Override
		public void trace(String msg, Throwable t) {
			handler().handle(Level.TRACE, msg, t);
		}

		@Override
		public boolean isTraceEnabled(Marker marker) {
			return true;
		}

		@Override
		public void trace(Marker marker, String msg) {
			handler().handle(Level.TRACE, msg);
		}

		@Override
		public void trace(Marker marker, String format, Object arg) {
			handler().handle(Level.TRACE, format, arg);
		}

		@Override
		public void trace(Marker marker, String format, Object arg1, Object arg2) {
			handler().handle(Level.TRACE, format, arg1, arg2);
		}

		@Override
		public void trace(Marker marker, String format, Object... argArray) {
			handler().handleArray(Level.TRACE, format, argArray);
		}

		@Override
		public void trace(Marker marker, String msg, Throwable t) {
			handler().handle(Level.TRACE, msg, t);
		}
	}

}

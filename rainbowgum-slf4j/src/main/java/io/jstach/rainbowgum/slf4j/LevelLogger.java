package io.jstach.rainbowgum.slf4j;

import org.slf4j.Logger;
import org.slf4j.Marker;
import org.slf4j.event.Level;
import org.slf4j.spi.LoggingEventBuilder;

sealed interface LevelLogger extends BaseLogger, Logger {

	record OffLogger(String loggerName) implements LevelLogger {
		@Override
		public void handle(io.jstach.rainbowgum.LogEvent event) {
		}
	}

	public static LevelLogger of(Level level, String loggerName, io.jstach.rainbowgum.LogAppender appender) {
		return switch (level) {
			case ERROR -> new ErrorLogger(loggerName, appender);
			case WARN -> new WarnLogger(loggerName, appender);
			case INFO -> new InfoLogger(loggerName, appender);
			case DEBUG -> new DebugLogger(loggerName, appender);
			case TRACE -> new TraceLogger(loggerName, appender);
		};
	}

	record ErrorLogger(String loggerName, io.jstach.rainbowgum.LogAppender appender) implements LevelLogger {

		@Override
		public void handle(io.jstach.rainbowgum.LogEvent event) {
			appender.append(event);
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
			handle(Level.ERROR, msg);
		}

		@Override
		public void error(String format, Object arg) {
			handle(Level.ERROR, format, arg);
		}

		@Override
		public void error(String format, Object arg1, Object arg2) {
			handle(Level.ERROR, format, arg1, arg2);
		}

		@Override
		public void error(String format, Object... arguments) {
			handle(Level.ERROR, format, arguments);
		}

		@Override
		public boolean isErrorEnabled(Marker marker) {
			return true;
		}

		@Override
		public void error(Marker marker, String msg) {
			handle(Level.ERROR, msg);
		}

		@Override
		public void error(Marker marker, String format, Object arg) {
			handle(Level.ERROR, format, arg);
		}

		@Override
		public void error(Marker marker, String format, Object arg1, Object arg2) {
			handle(Level.ERROR, format, arg1, arg2);
		}

		@Override
		public void error(Marker marker, String format, Object... argArray) {
			handle(Level.ERROR, format, argArray);
		}

		@Override
		public void error(Marker marker, String msg, Throwable t) {
			handle(Level.ERROR, msg, t);
		}
	}

	record WarnLogger(String loggerName, io.jstach.rainbowgum.LogAppender appender) implements LevelLogger {

		@Override
		public void handle(io.jstach.rainbowgum.LogEvent event) {
			appender.append(event);
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
			handle(Level.WARN, msg);
		}

		@Override
		public void warn(String format, Object arg) {
			handle(Level.WARN, format, arg);
		}

		@Override
		public void warn(String format, Object arg1, Object arg2) {
			handle(Level.WARN, format, arg1, arg2);
		}

		@Override
		public void warn(String format, Object... arguments) {
			handle(Level.WARN, format, arguments);
		}

		@Override
		public boolean isWarnEnabled(Marker marker) {
			return true;
		}

		@Override
		public void warn(Marker marker, String msg) {
			handle(Level.WARN, msg);
		}

		@Override
		public void warn(Marker marker, String format, Object arg) {
			handle(Level.WARN, format, arg);
		}

		@Override
		public void warn(Marker marker, String format, Object arg1, Object arg2) {
			handle(Level.WARN, format, arg1, arg2);
		}

		@Override
		public void warn(Marker marker, String format, Object... argArray) {
			handle(Level.WARN, format, argArray);
		}

		@Override
		public void warn(Marker marker, String msg, Throwable t) {
			handle(Level.WARN, msg, t);
		}
	}

	record InfoLogger(String loggerName, io.jstach.rainbowgum.LogAppender appender) implements LevelLogger {

		@Override
		public void handle(io.jstach.rainbowgum.LogEvent event) {
			appender.append(event);
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
			handle(Level.INFO, msg);
		}

		@Override
		public void info(String format, Object arg) {
			handle(Level.INFO, format, arg);
		}

		@Override
		public void info(String format, Object arg1, Object arg2) {
			handle(Level.INFO, format, arg1, arg2);
		}

		@Override
		public void info(String format, Object... arguments) {
			handle(Level.INFO, format, arguments);
		}

		@Override
		public boolean isInfoEnabled(Marker marker) {
			return true;
		}

		@Override
		public void info(Marker marker, String msg) {
			handle(Level.INFO, msg);
		}

		@Override
		public void info(Marker marker, String format, Object arg) {
			handle(Level.INFO, format, arg);
		}

		@Override
		public void info(Marker marker, String format, Object arg1, Object arg2) {
			handle(Level.INFO, format, arg1, arg2);
		}

		@Override
		public void info(Marker marker, String format, Object... argArray) {
			handle(Level.INFO, format, argArray);
		}

		@Override
		public void info(Marker marker, String msg, Throwable t) {
			handle(Level.INFO, msg, t);
		}
	}

	record DebugLogger(String loggerName, io.jstach.rainbowgum.LogAppender appender) implements LevelLogger {

		@Override
		public void handle(io.jstach.rainbowgum.LogEvent event) {
			appender.append(event);
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
			handle(Level.DEBUG, msg);
		}

		@Override
		public void debug(String format, Object arg) {
			handle(Level.DEBUG, format, arg);
		}

		@Override
		public void debug(String format, Object arg1, Object arg2) {
			handle(Level.DEBUG, format, arg1, arg2);
		}

		@Override
		public void debug(String format, Object... arguments) {
			handle(Level.DEBUG, format, arguments);
		}

		@Override
		public boolean isDebugEnabled(Marker marker) {
			return true;
		}

		@Override
		public void debug(Marker marker, String msg) {
			handle(Level.DEBUG, msg);
		}

		@Override
		public void debug(Marker marker, String format, Object arg) {
			handle(Level.DEBUG, format, arg);
		}

		@Override
		public void debug(Marker marker, String format, Object arg1, Object arg2) {
			handle(Level.DEBUG, format, arg1, arg2);
		}

		@Override
		public void debug(Marker marker, String format, Object... argArray) {
			handle(Level.DEBUG, format, argArray);
		}

		@Override
		public void debug(Marker marker, String msg, Throwable t) {
			handle(Level.DEBUG, msg, t);
		}
	}

	record TraceLogger(String loggerName, io.jstach.rainbowgum.LogAppender appender) implements LevelLogger {

		@Override
		public void handle(io.jstach.rainbowgum.LogEvent event) {
			appender.append(event);
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
			handle(Level.TRACE, msg);
		}

		@Override
		public void trace(String format, Object arg) {
			handle(Level.TRACE, format, arg);
		}

		@Override
		public void trace(String format, Object arg1, Object arg2) {
			handle(Level.TRACE, format, arg1, arg2);
		}

		@Override
		public void trace(String format, Object... arguments) {
			handle(Level.TRACE, format, arguments);
		}

		@Override
		public boolean isTraceEnabled(Marker marker) {
			return true;
		}

		@Override
		public void trace(Marker marker, String msg) {
			handle(Level.TRACE, msg);
		}

		@Override
		public void trace(Marker marker, String format, Object arg) {
			handle(Level.TRACE, format, arg);
		}

		@Override
		public void trace(Marker marker, String format, Object arg1, Object arg2) {
			handle(Level.TRACE, format, arg1, arg2);
		}

		@Override
		public void trace(Marker marker, String format, Object... argArray) {
			handle(Level.TRACE, format, argArray);
		}

		@Override
		public void trace(Marker marker, String msg, Throwable t) {
			handle(Level.TRACE, msg, t);
		}
	}

}

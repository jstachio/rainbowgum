package io.jstach.rainbowgum.tomcat;

import java.lang.System.Logger.Level;

import org.apache.juli.logging.Log;
import org.eclipse.jdt.annotation.Nullable;

import io.jstach.rainbowgum.LogEvent;
import io.jstach.rainbowgum.LogEventLogger;

interface TomcatLevelLog extends Log {

	String loggerName();

	LogEventLogger eventLogger();

	default void log(Level level, @Nullable Object obj) {
		log(level, obj, null);
	}

	default void log(Level level, @Nullable Object obj, @Nullable Throwable t) {
		// We do not need to check if the route is enabled.
		// We are assuming level logging mode.
		String loggerName = loggerName();
		@Nullable
		String formattedMessage = obj == null ? null : obj.toString();
		LogEvent event = LogEvent.of(level, loggerName, formattedMessage, t);
		eventLogger().log(event);
	}

	record TraceLevelLog(String loggerName, LogEventLogger eventLogger) implements TomcatLevelLog {
		@Override
		public boolean isTraceEnabled() {
			return true;
		}

		@Override
		public boolean isDebugEnabled() {
			return true;
		}

		@Override
		public boolean isInfoEnabled() {
			return true;
		}

		@Override
		public boolean isWarnEnabled() {
			return true;
		}

		@Override
		public boolean isErrorEnabled() {
			return true;
		}

		@Override
		public boolean isFatalEnabled() {
			return isErrorEnabled();
		}

		@Override
		public void trace(@Nullable Object message) {
			log(Level.TRACE, message);
		}

		@Override
		public void trace(@Nullable Object message, @Nullable Throwable throwable) {
			log(Level.TRACE, message, throwable);
		}

		@Override
		public void debug(@Nullable Object message) {
			log(Level.DEBUG, message);
		}

		@Override
		public void debug(@Nullable Object message, @Nullable Throwable throwable) {
			log(Level.DEBUG, message, throwable);
		}

		@Override
		public void info(@Nullable Object message) {
			log(Level.INFO, message);
		}

		@Override
		public void info(@Nullable Object message, @Nullable Throwable throwable) {
			log(Level.INFO, message, throwable);
		}

		@Override
		public void warn(@Nullable Object message) {
			log(Level.WARNING, message);
		}

		@Override
		public void warn(@Nullable Object message, @Nullable Throwable throwable) {
			log(Level.WARNING, message, throwable);
		}

		@Override
		public void error(@Nullable Object message) {
			log(Level.ERROR, message);
		}

		@Override
		public void error(@Nullable Object message, @Nullable Throwable throwable) {
			log(Level.ERROR, message, throwable);
		}

		@Override
		public void fatal(Object message) {
			error(message);
		}

		@Override
		public void fatal(Object message, Throwable t) {
			error(message, t);

		}

	}

	record DebugLevelLog(String loggerName, LogEventLogger eventLogger) implements TomcatLevelLog {
		@Override
		public boolean isTraceEnabled() {
			return false;
		}

		@Override
		public boolean isDebugEnabled() {
			return true;
		}

		@Override
		public boolean isInfoEnabled() {
			return true;
		}

		@Override
		public boolean isWarnEnabled() {
			return true;
		}

		@Override
		public boolean isErrorEnabled() {
			return true;
		}

		@Override
		public boolean isFatalEnabled() {
			return isErrorEnabled();
		}

		@Override
		public void trace(@Nullable Object message) {
		}

		@Override
		public void trace(@Nullable Object message, @Nullable Throwable throwable) {
		}

		@Override
		public void debug(@Nullable Object message) {
			log(Level.DEBUG, message);
		}

		@Override
		public void debug(@Nullable Object message, @Nullable Throwable throwable) {
			log(Level.DEBUG, message, throwable);
		}

		@Override
		public void info(@Nullable Object message) {
			log(Level.INFO, message);
		}

		@Override
		public void info(@Nullable Object message, @Nullable Throwable throwable) {
			log(Level.INFO, message, throwable);
		}

		@Override
		public void warn(@Nullable Object message) {
			log(Level.WARNING, message);
		}

		@Override
		public void warn(@Nullable Object message, @Nullable Throwable throwable) {
			log(Level.WARNING, message, throwable);
		}

		@Override
		public void error(@Nullable Object message) {
			log(Level.ERROR, message);
		}

		@Override
		public void error(@Nullable Object message, @Nullable Throwable throwable) {
			log(Level.ERROR, message, throwable);
		}

		@Override
		public void fatal(Object message) {
			error(message);
		}

		@Override
		public void fatal(Object message, Throwable t) {
			error(message, t);

		}

	}

	record InfoLevelLog(String loggerName, LogEventLogger eventLogger) implements TomcatLevelLog {
		@Override
		public boolean isTraceEnabled() {
			return false;
		}

		@Override
		public boolean isDebugEnabled() {
			return false;
		}

		@Override
		public boolean isInfoEnabled() {
			return true;
		}

		@Override
		public boolean isWarnEnabled() {
			return true;
		}

		@Override
		public boolean isErrorEnabled() {
			return true;
		}

		@Override
		public boolean isFatalEnabled() {
			return isErrorEnabled();
		}

		@Override
		public void trace(@Nullable Object message) {
		}

		@Override
		public void trace(@Nullable Object message, @Nullable Throwable throwable) {
		}

		@Override
		public void debug(@Nullable Object message) {
		}

		@Override
		public void debug(@Nullable Object message, @Nullable Throwable throwable) {
		}

		@Override
		public void info(@Nullable Object message) {
			log(Level.INFO, message);
		}

		@Override
		public void info(@Nullable Object message, @Nullable Throwable throwable) {
			log(Level.INFO, message, throwable);
		}

		@Override
		public void warn(@Nullable Object message) {
			log(Level.WARNING, message);
		}

		@Override
		public void warn(@Nullable Object message, @Nullable Throwable throwable) {
			log(Level.WARNING, message, throwable);
		}

		@Override
		public void error(@Nullable Object message) {
			log(Level.ERROR, message);
		}

		@Override
		public void error(@Nullable Object message, @Nullable Throwable throwable) {
			log(Level.ERROR, message, throwable);
		}

		@Override
		public void fatal(Object message) {
			error(message);
		}

		@Override
		public void fatal(Object message, Throwable t) {
			error(message, t);

		}

	}

	record WarnLevelLog(String loggerName, LogEventLogger eventLogger) implements TomcatLevelLog {
		@Override
		public boolean isTraceEnabled() {
			return false;
		}

		@Override
		public boolean isDebugEnabled() {
			return false;
		}

		@Override
		public boolean isInfoEnabled() {
			return false;
		}

		@Override
		public boolean isWarnEnabled() {
			return true;
		}

		@Override
		public boolean isErrorEnabled() {
			return true;
		}

		@Override
		public boolean isFatalEnabled() {
			return isErrorEnabled();
		}

		@Override
		public void trace(@Nullable Object message) {
		}

		@Override
		public void trace(@Nullable Object message, @Nullable Throwable throwable) {
		}

		@Override
		public void debug(@Nullable Object message) {
		}

		@Override
		public void debug(@Nullable Object message, @Nullable Throwable throwable) {
		}

		@Override
		public void info(@Nullable Object message) {
		}

		@Override
		public void info(@Nullable Object message, @Nullable Throwable throwable) {
		}

		@Override
		public void warn(@Nullable Object message) {
			log(Level.WARNING, message);
		}

		@Override
		public void warn(@Nullable Object message, @Nullable Throwable throwable) {
			log(Level.WARNING, message, throwable);
		}

		@Override
		public void error(@Nullable Object message) {
			log(Level.ERROR, message);
		}

		@Override
		public void error(@Nullable Object message, @Nullable Throwable throwable) {
			log(Level.ERROR, message, throwable);
		}

		@Override
		public void fatal(Object message) {
			error(message);
		}

		@Override
		public void fatal(Object message, Throwable t) {
			error(message, t);

		}

	}

	record ErrorLevelLog(String loggerName, LogEventLogger eventLogger) implements TomcatLevelLog {
		@Override
		public boolean isTraceEnabled() {
			return false;
		}

		@Override
		public boolean isDebugEnabled() {
			return false;
		}

		@Override
		public boolean isInfoEnabled() {
			return false;
		}

		@Override
		public boolean isWarnEnabled() {
			return false;
		}

		@Override
		public boolean isErrorEnabled() {
			return true;
		}

		@Override
		public boolean isFatalEnabled() {
			return isErrorEnabled();
		}

		@Override
		public void trace(@Nullable Object message) {
		}

		@Override
		public void trace(@Nullable Object message, @Nullable Throwable throwable) {
		}

		@Override
		public void debug(@Nullable Object message) {
		}

		@Override
		public void debug(@Nullable Object message, @Nullable Throwable throwable) {
		}

		@Override
		public void info(@Nullable Object message) {
		}

		@Override
		public void info(@Nullable Object message, @Nullable Throwable throwable) {
		}

		@Override
		public void warn(@Nullable Object message) {
		}

		@Override
		public void warn(@Nullable Object message, @Nullable Throwable throwable) {
		}

		@Override
		public void error(@Nullable Object message) {
			log(Level.ERROR, message);
		}

		@Override
		public void error(@Nullable Object message, @Nullable Throwable throwable) {
			log(Level.ERROR, message, throwable);
		}

		@Override
		public void fatal(Object message) {
			error(message);
		}

		@Override
		public void fatal(Object message, Throwable t) {
			error(message, t);

		}

	}

	record OffLevelLog(String loggerName, LogEventLogger eventLogger) implements TomcatLevelLog {
		@Override
		public boolean isTraceEnabled() {
			return false;
		}

		@Override
		public boolean isDebugEnabled() {
			return false;
		}

		@Override
		public boolean isInfoEnabled() {
			return false;
		}

		@Override
		public boolean isWarnEnabled() {
			return false;
		}

		@Override
		public boolean isErrorEnabled() {
			return false;
		}

		@Override
		public boolean isFatalEnabled() {
			return isErrorEnabled();
		}

		@Override
		public void trace(@Nullable Object message) {
		}

		@Override
		public void trace(@Nullable Object message, @Nullable Throwable throwable) {
		}

		@Override
		public void debug(@Nullable Object message) {
		}

		@Override
		public void debug(@Nullable Object message, @Nullable Throwable throwable) {
		}

		@Override
		public void info(@Nullable Object message) {
		}

		@Override
		public void info(@Nullable Object message, @Nullable Throwable throwable) {
		}

		@Override
		public void warn(@Nullable Object message) {
		}

		@Override
		public void warn(@Nullable Object message, @Nullable Throwable throwable) {
		}

		@Override
		public void error(@Nullable Object message) {
		}

		@Override
		public void error(@Nullable Object message, @Nullable Throwable throwable) {
		}

		@Override
		public void fatal(Object message) {
			error(message);
		}

		@Override
		public void fatal(Object message, Throwable t) {
			error(message, t);

		}

	}

}

package io.jstach.rainbowgum.systemlogger;

import java.util.Objects;
import java.util.ResourceBundle;
import java.util.function.Supplier;

import org.jspecify.annotations.Nullable;

import io.jstach.rainbowgum.LogEvent;
import io.jstach.rainbowgum.LogEventLogger;

record LevelSystemLogger(String loggerName, int level, LogEventLogger logger,
		SystemLoggerEventFactory eventFactory) implements System.Logger {

	static System.Logger of(String loggerName, Level level, LogEventLogger logger) {
		if (level == Level.OFF) {
			return new OffSystemLogger(loggerName);
		}
		return new LevelSystemLogger(loggerName, SystemLoggerEventFactory.fixLevel(level).getSeverity(), logger,
				new SystemLoggerEventFactory(loggerName));
	}

	record OffSystemLogger(String loggerName) implements System.Logger {

		@Override
		public String getName() {
			return loggerName;
		}

		@Override
		public boolean isLoggable(Level level) {
			return false;
		}

		@Override
		public void log(Level level, ResourceBundle bundle, String msg, Throwable thrown) {

		}

		@Override
		public void log(Level level, ResourceBundle bundle, String format, Object... params) {
		}
	}

	@Override
	public String getName() {
		return loggerName;
	}

	@Override
	public final boolean isLoggable(Level level) {
		// OFF.getSeverity() is Integer.MAX_VALUE, so without this guard the severity
		// comparison below would treat log(Level.OFF, ...) as loggable - real JDK
		// System.Logger implementations never actually log at OFF (see JDKSetupTest's
		// level==OFF-vs-loggerLevel!=OFF JUL-parity case).
		if (level == Level.OFF) {
			return false;
		}
		return SystemLoggerEventFactory.fixLevel(level).getSeverity() >= this.level;
	}

	private LogEvent event(Level level, @Nullable String formattedMessage, @Nullable Throwable throwable) {
		return eventFactory.eventNoArg(level, formattedMessage, throwable);
	}

	@Override
	public void log(Level level, @Nullable String msg) {
		if (isLoggable(level)) {
			String formattedMessage = msg;
			LogEvent event = event(level, formattedMessage, null);
			logger.log(event);
		}
	}

	@Override
	public void log(Level level, Supplier<String> msgSupplier) {
		if (isLoggable(level)) {
			String formattedMessage = msgSupplier.get();
			LogEvent event = event(level, formattedMessage, null);
			logger.log(event);
		}
	}

	@Override
	public void log(Level level, Object obj) {
		// Technically we should check if obj is null first.
		Objects.requireNonNull(obj, "obj");
		if (isLoggable(level)) {
			String formattedMessage = obj.toString();
			LogEvent event = event(level, formattedMessage, null);
			logger.log(event);
		}
	}

	@Override
	public void log(Level level, @Nullable String msg, @Nullable Throwable thrown) {
		if (isLoggable(level)) {
			String formattedMessage = msg;
			LogEvent event = event(level, formattedMessage, thrown);
			logger.log(event);
		}
	}

	@Override
	public void log(Level level, Supplier<String> msgSupplier, Throwable thrown) {
		if (isLoggable(level)) {
			String formattedMessage = msgSupplier.get();
			LogEvent event = event(level, formattedMessage, thrown);
			logger.log(event);
		}
	}

	@Override
	public void log(Level level, @Nullable String format, @Nullable Object... params) {
		if (isLoggable(level)) {
			LogEvent event = eventFactory.eventArgs(level, format, params);
			logger.log(event);
		}
	}

	@Override
	public void log(Level level, @Nullable ResourceBundle bundle, @Nullable String msg, @Nullable Throwable thrown) {
		if (isLoggable(level)) {
			String formattedMessage = eventFactory.message(bundle, msg);
			LogEvent event = event(level, formattedMessage, thrown);
			logger.log(event);
		}
	}

	@Override
	public void log(Level level, @Nullable ResourceBundle bundle, @Nullable String format, @Nullable Object... params) {
		if (isLoggable(level)) {
			String message = eventFactory.message(bundle, format);
			LogEvent event = eventFactory.eventArgs(level, message, params);
			logger.log(event);
		}

	}

}

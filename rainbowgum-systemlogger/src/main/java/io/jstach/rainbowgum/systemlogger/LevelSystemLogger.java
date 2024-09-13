package io.jstach.rainbowgum.systemlogger;

import static java.util.Objects.requireNonNullElse;

import java.time.Instant;
import java.util.ResourceBundle;
import java.util.function.Supplier;

import org.eclipse.jdt.annotation.Nullable;

import io.jstach.rainbowgum.KeyValues;
import io.jstach.rainbowgum.LogEvent;
import io.jstach.rainbowgum.LogEventLogger;
import io.jstach.rainbowgum.LogMessageFormatter.StandardMessageFormatter;

record LevelSystemLogger(String loggerName, int level, LogEventLogger logger) implements System.Logger {

	static System.Logger of(String loggerName, Level level, LogEventLogger logger) {
		if (level == Level.OFF) {
			return new OffSystemLogger(loggerName);
		}
		return new LevelSystemLogger(loggerName, fixLevel(level).getSeverity(), logger);
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

	static Level fixLevel(Level level) {
		if (level == Level.ALL) {
			return Level.TRACE;
		}
		return level;
	}

	@Override
	public String getName() {
		return loggerName;
	}

	@Override
	public final boolean isLoggable(Level level) {
		if (level == Level.OFF) {
			return false;
		}
		return fixLevel(level).getSeverity() >= this.level;
	}

	@Override
	public void log(Level level, @Nullable String msg) {
		if (isLoggable(level)) {
			String formattedMessage = msg == null ? "" : msg;
			LogEvent event = LogEvent.of(level, loggerName, formattedMessage, null);
			logger.log(event);
		}
	}

	@Override
	public void log(Level level, Supplier<String> msgSupplier) {
		if (isLoggable(level)) {
			String formattedMessage = requireNonNullElse(msgSupplier.get(), "");
			LogEvent event = LogEvent.of(level, loggerName, formattedMessage, null);
			logger.log(event);
		}
	}

	@Override
	public void log(Level level, @Nullable Object obj) {
		if (isLoggable(level)) {
			String formattedMessage = obj == null ? "" : obj.toString();
			LogEvent event = LogEvent.of(level, loggerName, formattedMessage, null);
			logger.log(event);
		}
	}

	@Override
	public void log(Level level, @Nullable String msg, @Nullable Throwable thrown) {
		if (isLoggable(level)) {
			String formattedMessage = requireNonNullElse(msg, "");
			LogEvent event = LogEvent.of(level, loggerName, formattedMessage, thrown);
			logger.log(event);
		}
	}

	@Override
	public void log(Level level, Supplier<String> msgSupplier, Throwable thrown) {
		if (isLoggable(level)) {
			String formattedMessage = requireNonNullElse(msgSupplier.get(), "");
			LogEvent event = LogEvent.of(level, loggerName, formattedMessage, thrown);
			logger.log(event);
		}
	}

	@Override
	public void log(Level level, @Nullable String format, @Nullable Object... params) {
		if (isLoggable(level)) {
			var currentThread = Thread.currentThread();
			Instant timestamp = Instant.now();
			String threadName = currentThread.getName();
			long threadId = currentThread.threadId();
			String message = requireNonNullElse(format, "");
			Throwable throwable = null;
			LogEvent event = LogEvent.ofAll(timestamp, threadName, threadId, level, loggerName, message, KeyValues.of(),
					throwable, StandardMessageFormatter.JUL, params);
			logger.log(event);
		}
	}

	@Override
	public void log(Level level, @Nullable ResourceBundle bundle, @Nullable String msg, @Nullable Throwable thrown) {
		if (isLoggable(level)) {
			String formattedMessage = requireNonNullElse(msg, "");
			LogEvent event = LogEvent.of(level, loggerName, formattedMessage, thrown);
			logger.log(event);
		}
	}

	@Override
	public void log(Level level, @Nullable ResourceBundle bundle, @Nullable String format, @Nullable Object... params) {
		if (isLoggable(level)) {
			var currentThread = Thread.currentThread();
			Instant timestamp = Instant.now();
			String threadName = currentThread.getName();
			long threadId = currentThread.threadId();
			String message = requireNonNullElse(format, "");
			Throwable throwable = null;
			LogEvent event = LogEvent.ofAll(timestamp, threadName, threadId, level, loggerName, message, KeyValues.of(),
					throwable, StandardMessageFormatter.JUL, params);
			logger.log(event);
		}

	}

}

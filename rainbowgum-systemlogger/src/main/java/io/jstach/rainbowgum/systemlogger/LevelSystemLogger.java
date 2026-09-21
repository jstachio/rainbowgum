package io.jstach.rainbowgum.systemlogger;

import java.util.MissingResourceException;
import java.util.Objects;
import java.util.ResourceBundle;
import java.util.function.Supplier;

import org.jspecify.annotations.Nullable;

import io.jstach.rainbowgum.LogEvent;
import io.jstach.rainbowgum.LogEventFactory;
import io.jstach.rainbowgum.LogEventLogger;
import io.jstach.rainbowgum.LogMessageFormatter;
import io.jstach.rainbowgum.LogMessageFormatter.StandardMessageFormatter;

record LevelSystemLogger(String loggerName, int level, LogEventLogger logger,
		LogEventFactory eventFactory) implements System.Logger {

	static System.Logger of(String loggerName, Level level, LogEventLogger logger) {
		if (level == Level.OFF) {
			return new OffSystemLogger(loggerName);
		}
		return new LevelSystemLogger(loggerName, fixLevel(level).getSeverity(), logger, eventFactory(loggerName));
	}

	/*
	 * java.lang.System.Logger's varargs log(...) overloads follow java.text.MessageFormat
	 * ({0}/{1}-style) placeholder conventions, not SLF4J's {}-style ones - override
	 * messageFormatter() rather than relying on LogEventFactory's own SLF4J default.
	 * Package-private (not private) - RainbowGumSystemLogger shares this exact same
	 * factory shape rather than duplicating it.
	 */
	static LogEventFactory eventFactory(String loggerName) {
		record JULLogEventFactory(String loggerName) implements LogEventFactory {

			@Override
			public LogMessageFormatter messageFormatter() {
				return StandardMessageFormatter.JUL;
			}

		}
		return new JULLogEventFactory(loggerName);
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

	final static @Nullable String getMessage(@Nullable ResourceBundle bundle, @Nullable String msg) {
		if (bundle == null || msg == null) {
			return msg;
		}
		try {
			return bundle.getString(msg);
		}
		catch (MissingResourceException ex) {
			return msg;
		}
		catch (ClassCastException ex) {
			return bundle.getObject(msg).toString();
		}
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
			String formattedMessage = getMessage(bundle, msg);
			LogEvent event = event(level, formattedMessage, thrown);
			logger.log(event);
		}
	}

	@Override
	public void log(Level level, @Nullable ResourceBundle bundle, @Nullable String format, @Nullable Object... params) {
		if (isLoggable(level)) {
			String message = getMessage(bundle, format);
			LogEvent event = eventFactory.eventArgs(level, message, params);
			logger.log(event);
		}

	}

}

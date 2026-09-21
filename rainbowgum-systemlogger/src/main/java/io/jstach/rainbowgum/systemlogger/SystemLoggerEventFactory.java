package io.jstach.rainbowgum.systemlogger;

import java.lang.System.Logger.Level;
import java.util.MissingResourceException;
import java.util.ResourceBundle;

import org.jspecify.annotations.Nullable;

import io.jstach.rainbowgum.LogEventFactory;
import io.jstach.rainbowgum.LogMessageFormatter;
import io.jstach.rainbowgum.LogMessageFormatter.StandardMessageFormatter;

/**
 * {@link LogEventFactory} shared by {@link LevelSystemLogger} and
 * {@link RainbowGumSystemLogger} - java.lang.System.Logger's varargs log(...) overloads
 * follow java.text.MessageFormat ({0}/{1}-style) placeholder conventions, not SLF4J's
 * {}-style ones, so {@link #messageFormatter()} is overridden accordingly; and
 * {@link #message(ResourceBundle, String)} resolves a ResourceBundle-keyed message the
 * way java.lang.System.Logger's own bundle-based log(...) overloads are documented to.
 */
record SystemLoggerEventFactory(String loggerName) implements LogEventFactory {

	@Override
	public LogMessageFormatter messageFormatter() {
		return StandardMessageFormatter.JUL;
	}

	@Nullable String message(@Nullable ResourceBundle bundle, @Nullable String msg) {
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

	/**
	 * {@link Level#ALL} has no corresponding {@link System.Logger} severity to compare
	 * against - both {@link LevelSystemLogger} and {@link RainbowGumSystemLogger} treat
	 * it as {@link Level#TRACE} (the least severe real level) instead.
	 * @param level level to fix.
	 * @return level with {@link Level#ALL} mapped to {@link Level#TRACE}.
	 */
	static Level fixLevel(Level level) {
		if (level == Level.ALL) {
			return Level.TRACE;
		}
		return level;
	}

}

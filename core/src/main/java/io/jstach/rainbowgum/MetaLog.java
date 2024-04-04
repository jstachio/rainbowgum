package io.jstach.rainbowgum;

import java.lang.System.Logger.Level;
import java.util.Objects;

/**
 * Logging about logging. Currently not really public API.
 *
 * @author agentgt
 * @hidden
 */
@SuppressWarnings("InvalidBlockTag")
public final class MetaLog {

	private MetaLog() {
	}

	/**
	 * Logs an error in the logging system.
	 * @param event event to log.
	 */
	public static void error(LogEvent event) {
		FailsafeAppender.INSTANCE.log(event);
	}

	/**
	 * Logs an error in the logging system.
	 * @param loggerName derived from class.
	 * @param throwable error to log.
	 */
	public static void error(Class<?> loggerName, Throwable throwable) {
		String m = Objects.requireNonNullElse(throwable.getMessage(), "exception");
		error(loggerName, m, throwable);
	}

	/**
	 * Logs an error in the logging system.
	 * @param loggerName derived from class.
	 * @param message error message.
	 * @param throwable error to log.
	 */
	public static void error(Class<?> loggerName, String message, Throwable throwable) {
		var event = LogEvent.of(Level.ERROR, loggerName.getName(), message, throwable);
		error(event);
	}

}

enum FailsafeAppender implements LogEventLogger {

	INSTANCE;

	@Override
	public void log(LogEvent event) {
		if (event.level().compareTo(Level.ERROR) >= 0) {
			var err = System.err;
			if (err != null) {
				err.append("[ERROR] - logging ");
				StringBuilder sb = new StringBuilder();
				event.formattedMessage(sb);
				err.append(sb.toString());

				var throwable = event.throwableOrNull();
				if (throwable != null) {
					throwable.printStackTrace(err);
				}
			}
		}
	}

}
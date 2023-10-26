package io.jstach.rainbowgum;

import java.lang.System.Logger.Level;

/**
 * Logging about logging. Currently not really public API.
 *
 * @author agentgt
 * @hidden
 */
public interface MetaLog {

	/**
	 * Logs an error in the logging system.
	 * @param event event to log.
	 */
	public static void error(LogEvent event) {
		FailsafeAppender.INSTANCE.append(event);
	}

	/**
	 * Logs an error in the logging system.
	 * @param loggerName derived from class.
	 * @param throwable error to log.
	 */
	public static void error(Class<?> loggerName, Throwable throwable) {
		error(loggerName, throwable.getMessage(), throwable);
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

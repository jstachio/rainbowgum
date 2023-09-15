package io.jstach.rainbowgum.slf4j;

import org.eclipse.jdt.annotation.Nullable;
import org.slf4j.Logger;
import org.slf4j.event.Level;

class Levels {

	public static boolean isEnabled(
			Logger logger,
			Level level) {
		return switch (level) {
			case DEBUG -> logger.isDebugEnabled();
			case ERROR -> logger.isErrorEnabled();
			case INFO -> logger.isInfoEnabled();
			case TRACE -> logger.isTraceEnabled();
			case WARN -> logger.isWarnEnabled();
		};
	}
	
	public static boolean isEnabled(System.Logger.Level current, System.Logger.Level level) {
		return current.getSeverity() <= level.getSeverity();
	}

	public static void log(
			Logger logger,
			Level level,
			String msg,
			@Nullable Throwable cause) {
		if (cause == null) {
			switch (level) {
				case DEBUG -> logger.debug(msg);
				case ERROR -> logger.error(msg);
				case INFO -> logger.info(msg);
				case TRACE -> logger.trace(msg);
				case WARN -> logger.warn(msg);
			};
		}
		else {
			switch (level) {
				case DEBUG -> logger.debug(msg, cause);
				case ERROR -> logger.error(msg, cause);
				case INFO -> logger.info(msg, cause);
				case TRACE -> logger.trace(msg, cause);
				case WARN -> logger.warn(msg, cause);
			};
		}
	}

	public static String toString(
			System.Logger.Level level) {
		return toSlf4jLevel(level).name();
	}

	static Level toSlf4jLevel(
			System.Logger.Level level) {
		return switch (level) {
			case DEBUG -> Level.DEBUG;
			case ALL -> Level.ERROR;
			case ERROR -> Level.ERROR;
			case INFO -> Level.INFO;
			case OFF -> Level.TRACE;
			case TRACE -> Level.TRACE;
			case WARNING -> Level.WARN;
		};
	}
	
	static System.Logger.Level toSystemLevel(
			Level level) {
		return switch (level) {
			case TRACE -> System.Logger.Level.TRACE;
			case DEBUG -> System.Logger.Level.DEBUG;
			case INFO -> System.Logger.Level.INFO;
			case WARN -> System.Logger.Level.WARNING;
			case ERROR -> System.Logger.Level.ERROR;
		};
	}
}

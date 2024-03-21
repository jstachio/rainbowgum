package io.jstach.rainbowgum.slf4j;

import static org.slf4j.event.EventConstants.DEBUG_INT;
import static org.slf4j.event.EventConstants.ERROR_INT;
import static org.slf4j.event.EventConstants.INFO_INT;
import static org.slf4j.event.EventConstants.TRACE_INT;
import static org.slf4j.event.EventConstants.WARN_INT;

import org.slf4j.event.Level;

class Levels {

	static final int OFF_INT = -1;

	static String toString(System.Logger.Level level) {
		return toSlf4jLevel(level).name();
	}

	static int toSlf4jInt(System.Logger.Level level) {
		return switch (level) {
			case DEBUG -> DEBUG_INT;
			case ALL -> ERROR_INT;
			case ERROR -> ERROR_INT;
			case INFO -> INFO_INT;
			case OFF -> OFF_INT;
			case TRACE -> TRACE_INT;
			case WARNING -> WARN_INT;
		};
	}

	static Level toSlf4jLevel(System.Logger.Level level) {
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

	static System.Logger.Level toSystemLevel(Level level) {
		return switch (level) {
			case TRACE -> System.Logger.Level.TRACE;
			case DEBUG -> System.Logger.Level.DEBUG;
			case INFO -> System.Logger.Level.INFO;
			case WARN -> System.Logger.Level.WARNING;
			case ERROR -> System.Logger.Level.ERROR;
		};
	}

}

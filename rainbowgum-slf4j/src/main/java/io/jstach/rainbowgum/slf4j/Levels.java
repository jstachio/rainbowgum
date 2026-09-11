package io.jstach.rainbowgum.slf4j;

import org.slf4j.event.Level;

class Levels {

	static final int OFF_INT = Integer.MIN_VALUE;

	static Level toSlf4jLevel(System.Logger.Level level) {
		return switch (level) {
			case DEBUG -> Level.DEBUG;
			case ALL -> Level.TRACE;
			case ERROR -> Level.ERROR;
			case INFO -> Level.INFO;
			case OFF -> Level.TRACE;
			case TRACE -> Level.TRACE;
			case WARNING -> Level.WARN;
		};
	}

	static Level toSlf4jLevel(int locationAwareLevelInt) {
		/*
		 * org.slf4j.spi.LocationAwareLogger.TRACE_INT/DEBUG_INT/INFO_INT/WARN_INT/
		 * ERROR_INT. Not an enum on that interface, just raw ints bridges pass in, so
		 * anything outside the five known values (a bridge misbehaving, or a future SLF4J
		 * level) falls back to the closest defined level rather than throwing.
		 */
		if (locationAwareLevelInt <= 0) {
			return Level.TRACE;
		}
		else if (locationAwareLevelInt <= 10) {
			return Level.DEBUG;
		}
		else if (locationAwareLevelInt <= 20) {
			return Level.INFO;
		}
		else if (locationAwareLevelInt <= 30) {
			return Level.WARN;
		}
		return Level.ERROR;
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

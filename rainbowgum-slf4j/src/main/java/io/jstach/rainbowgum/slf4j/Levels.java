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

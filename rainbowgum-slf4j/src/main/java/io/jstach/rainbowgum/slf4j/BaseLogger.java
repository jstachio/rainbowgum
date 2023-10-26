package io.jstach.rainbowgum.slf4j;

import org.slf4j.Logger;
import org.slf4j.MDC;
import org.slf4j.Marker;
import org.slf4j.event.Level;
import org.slf4j.spi.LoggingEventBuilder;
import org.slf4j.spi.MDCAdapter;
import org.slf4j.spi.NOPLoggingEventBuilder;

import io.jstach.rainbowgum.KeyValues;
import io.jstach.rainbowgum.LogEvent;

interface BaseLogger extends EventCreator<Level>, Logger {

	@Override
	default java.lang.System.Logger.Level translateLevel(Level level) {
		return Levels.toSystemLevel(level);
	}

	default void handle(Level level, String msg) {
		handle(event0(level, msg));
	}

	default void handle(Level level, String format, Throwable throwable) {
		handle(event(level, format, throwable));
	}

	default void handle(Level level, String format, Object arg) {
		handle(event1(level, format, arg));
	}

	default void handle(Level level, String format, Object arg1, Object arg2) {
		handle(event2(level, format, arg1, arg2));
	}

	default void handleArray(Level level, String format, Object[] args) {
		handle(eventArray(level, format, args));
	}

	@Override
	default KeyValues keyValues() {
		MDCAdapter adapter = MDC.getMDCAdapter();
		if (adapter instanceof RainbowGumMDCAdapter simpleAdapter) {
			var m = simpleAdapter.getMutableKeyValues();
			if (m != null) {
				return m;
			}
		}
		return KeyValues.of();
	}

	void handle(LogEvent event);

	@Override
	default String getName() {
		return loggerName();
	}

	@Override
	default boolean isTraceEnabled() {
		return false;
	}

	@Override
	default void trace(String msg) {
	}

	@Override
	default void trace(String format, Object arg) {
	}

	@Override
	default void trace(String format, Object arg1, Object arg2) {
	}

	@Override
	default void trace(String format, Object... arguments) {
	}

	@Override
	default void trace(String msg, Throwable t) {
	}

	@Override
	default boolean isTraceEnabled(Marker marker) {
		return false;
	}

	@Override
	default LoggingEventBuilder atTrace() {
		return NOPLoggingEventBuilder.singleton();
	}

	@Override
	default void trace(Marker marker, String msg) {
	}

	@Override
	default void trace(Marker marker, String format, Object arg) {
	}

	@Override
	default void trace(Marker marker, String format, Object arg1, Object arg2) {
	}

	@Override
	default void trace(Marker marker, String format, Object... argArray) {
	}

	@Override
	default void trace(Marker marker, String msg, Throwable t) {
	}

	@Override
	default boolean isDebugEnabled() {
		return false;
	}

	@Override
	default void debug(String msg) {
	}

	@Override
	default void debug(String format, Object arg) {
	}

	@Override
	default void debug(String format, Object arg1, Object arg2) {
	}

	@Override
	default void debug(String format, Object... arguments) {
	}

	@Override
	default void debug(String msg, Throwable t) {

	}

	@Override
	default boolean isDebugEnabled(Marker marker) {

		return false;
	}

	@Override
	default void debug(Marker marker, String msg) {

	}

	@Override
	default void debug(Marker marker, String format, Object arg) {

	}

	@Override
	default void debug(Marker marker, String format, Object arg1, Object arg2) {

	}

	@Override
	default void debug(Marker marker, String format, Object... arguments) {

	}

	@Override
	default void debug(Marker marker, String msg, Throwable t) {

	}

	@Override
	default LoggingEventBuilder atDebug() {
		return NOPLoggingEventBuilder.singleton();
	}

	@Override
	default boolean isInfoEnabled() {

		return false;
	}

	@Override
	default void info(String msg) {

	}

	@Override
	default void info(String format, Object arg) {

	}

	@Override
	default void info(String format, Object arg1, Object arg2) {

	}

	@Override
	default void info(String format, Object... arguments) {

	}

	@Override
	default void info(String msg, Throwable t) {

	}

	@Override
	default boolean isInfoEnabled(Marker marker) {
		return false;
	}

	@Override
	default void info(Marker marker, String msg) {

	}

	@Override
	default void info(Marker marker, String format, Object arg) {

	}

	@Override
	default void info(Marker marker, String format, Object arg1, Object arg2) {

	}

	@Override
	default void info(Marker marker, String format, Object... arguments) {

	}

	@Override
	default void info(Marker marker, String msg, Throwable t) {

	}

	@Override
	default LoggingEventBuilder atInfo() {
		return NOPLoggingEventBuilder.singleton();
	}

	@Override
	default boolean isWarnEnabled() {

		return false;
	}

	@Override
	default void warn(String msg) {
	}

	@Override
	default void warn(String format, Object arg) {
	}

	@Override
	default void warn(String format, Object... arguments) {
	}

	@Override
	default void warn(String format, Object arg1, Object arg2) {

	}

	@Override
	default void warn(String msg, Throwable t) {

	}

	@Override
	default boolean isWarnEnabled(Marker marker) {
		return false;
	}

	@Override
	default void warn(Marker marker, String msg) {
	}

	@Override
	default void warn(Marker marker, String format, Object arg) {
	}

	@Override
	default void warn(Marker marker, String format, Object arg1, Object arg2) {

	}

	@Override
	default void warn(Marker marker, String format, Object... arguments) {

	}

	@Override
	default void warn(Marker marker, String msg, Throwable t) {
	}

	@Override
	default LoggingEventBuilder atWarn() {
		return NOPLoggingEventBuilder.singleton();
	}

	@Override
	default boolean isErrorEnabled() {

		return false;
	}

	@Override
	default void error(String msg) {

	}

	@Override
	default void error(String format, Object arg) {

	}

	@Override
	default void error(String format, Object arg1, Object arg2) {

	}

	@Override
	default void error(String format, Object... arguments) {

	}

	@Override
	default void error(String msg, Throwable t) {

	}

	@Override
	default boolean isErrorEnabled(Marker marker) {

		return false;
	}

	@Override
	default void error(Marker marker, String msg) {

	}

	@Override
	default void error(Marker marker, String format, Object arg) {

	}

	@Override
	default void error(Marker marker, String format, Object arg1, Object arg2) {
	}

	@Override
	default void error(Marker marker, String format, Object... arguments) {
	}

	@Override
	default void error(Marker marker, String msg, Throwable t) {
	}

	@Override
	default LoggingEventBuilder atError() {
		return NOPLoggingEventBuilder.singleton();
	}

}

package io.jstach.rainbowgum.slf4j;

import org.eclipse.jdt.annotation.Nullable;

import io.jstach.rainbowgum.KeyValues;
import io.jstach.rainbowgum.LogEvent;
import io.jstach.rainbowgum.LogMessageFormatter;

interface EventCreator<LEVEL> {

	public String loggerName();

	public System.Logger.Level translateLevel(LEVEL level);

	public KeyValues keyValues();

	default LogMessageFormatter messageFormatter() {
		return LogMessageFormatter.StandardMessageFormatter.SLF4J;
	}

	default LogEvent event(LEVEL level, String formattedMessage, @Nullable Throwable throwable) {
		var sysLevel = translateLevel(level);
		var loggerName = loggerName();
		var keyValues = keyValues();
		return LogEvent.of(sysLevel, loggerName, formattedMessage, keyValues, throwable);
	}

	default LogEvent event0(LEVEL level, String formattedMessage) {
		return event(level, formattedMessage, null);
	}

	default LogEvent event1(LEVEL level, String message, Object arg1) {
		var sysLevel = translateLevel(level);
		var loggerName = loggerName();
		var keyValues = keyValues();
		return LogEvent.of(sysLevel, loggerName, message, keyValues, messageFormatter(), arg1);
	}

	default LogEvent event2(LEVEL level, String message, Object arg1, Object arg2) {
		var sysLevel = translateLevel(level);
		var loggerName = loggerName();
		var keyValues = keyValues();
		return LogEvent.of(sysLevel, loggerName, message, keyValues, messageFormatter(), arg1, arg2);
	}

	default LogEvent eventArray(LEVEL level, String message, Object[] args) {
		var sysLevel = translateLevel(level);
		var loggerName = loggerName();
		var keyValues = keyValues();
		return LogEvent.ofArgs(sysLevel, loggerName, message, keyValues, messageFormatter(), args);
	}

}
package io.jstach.rainbowgum.slf4j;

import java.time.Instant;

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

	default LogEvent event(LEVEL level, @Nullable String formattedMessage, @Nullable Throwable throwable) {
		var sysLevel = translateLevel(level);
		var loggerName = loggerName();
		var keyValues = keyValues();
		var currentThread = Thread.currentThread();
		return LogEvent.of(Instant.now(), currentThread.getName(), currentThread.threadId(), sysLevel, loggerName,
				formattedMessage, keyValues, throwable);
	}

	default LogEvent event0(LEVEL level, @Nullable String formattedMessage) {
		return event(level, formattedMessage, null);
	}

	default LogEvent event1(LEVEL level, @Nullable String message, Object arg1) {
		var sysLevel = translateLevel(level);
		var loggerName = loggerName();
		var keyValues = keyValues();
		var currentThread = Thread.currentThread();
		return LogEvent.ofOneArg(Instant.now(), currentThread.getName(), currentThread.threadId(), sysLevel, loggerName,
				message, keyValues, messageFormatter(), arg1);
	}

	default LogEvent event2(LEVEL level, @Nullable String message, Object arg1, Object arg2) {
		var sysLevel = translateLevel(level);
		var loggerName = loggerName();
		var keyValues = keyValues();
		var currentThread = Thread.currentThread();
		return LogEvent.ofTwoArgs(Instant.now(), currentThread.getName(), currentThread.threadId(), sysLevel,
				loggerName, message, keyValues, messageFormatter(), arg1, arg2);
	}

	default LogEvent eventArray(LEVEL level, @Nullable String message, Object[] args) {
		var sysLevel = translateLevel(level);
		var loggerName = loggerName();
		var keyValues = keyValues();
		var currentThread = Thread.currentThread();
		return LogEvent.ofAll(Instant.now(), currentThread.getName(), currentThread.threadId(), sysLevel, loggerName,
				message, keyValues, null, messageFormatter(), args);
	}

}

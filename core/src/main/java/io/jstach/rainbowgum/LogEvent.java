package io.jstach.rainbowgum;

import java.time.Instant;
import java.util.Map;

import org.eclipse.jdt.annotation.Nullable;

public record LogEvent(Instant timeStamp, String threadName, long threadId, System.Logger.Level level,
		String loggerName, String formattedMessage, Map<String, String> keyValues, @Nullable Throwable throwable) {

	public @Nullable Throwable getThrowable() {
		return throwable();
	}

	@SuppressWarnings("exports")
	public Map<String, @Nullable String> getKeyValues() {
		return keyValues;
	}

	public static LogEvent of(System.Logger.Level level, String loggerName, String formattedMessage,
			Map<String, String> keyValues, @Nullable Throwable throwable) {
		Instant timeStamp = Instant.now();
		Thread currentThread = Thread.currentThread();
		String threadName = currentThread.getName();
		long threadId = currentThread.getId();

		return new LogEvent(timeStamp, threadName, threadId, level, loggerName, formattedMessage, keyValues, throwable);
	}

	public static LogEvent of(System.Logger.Level level, String loggerName, String formattedMessage,
			@Nullable Throwable throwable) {
		return of(level, loggerName, formattedMessage, Map.of(), throwable);
	}
}

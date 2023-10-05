package io.jstach.rainbowgum;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.time.Instant;

import org.eclipse.jdt.annotation.Nullable;

import io.jstach.rainbowgum.KeyValues.MutableKeyValues;
import io.jstach.rainbowgum.format.SLF4JMessageFormatter;

public sealed interface LogEvent {

	public static LogEvent of(System.Logger.Level level, String loggerName, String formattedMessage,
			KeyValues keyValues, @Nullable Throwable throwable) {
		Instant timeStamp = Instant.now();
		Thread currentThread = Thread.currentThread();
		String threadName = currentThread.getName();
		long threadId = currentThread.threadId();

		return new DefaultLogEvent(timeStamp, threadName, threadId, level, loggerName, formattedMessage, keyValues,
				throwable);
	}

	public static LogEvent of(System.Logger.Level level, String loggerName, String formattedMessage,
			@Nullable Throwable throwable) {
		return of(level, loggerName, formattedMessage, KeyValues.of(), throwable);
	}

	public static LogEvent of(System.Logger.Level level, String loggerName, String message, KeyValues keyValues,
			Object arg1) {
		Instant timeStamp = Instant.now();
		Thread currentThread = Thread.currentThread();
		String threadName = currentThread.getName();
		long threadId = currentThread.threadId();
		return new OneArgLogEvent(timeStamp, threadName, threadId, level, loggerName, message, keyValues, arg1);
	}

	public static LogEvent of(System.Logger.Level level, String loggerName, String message, KeyValues keyValues,
			Object arg1, Object arg2) {
		Instant timeStamp = Instant.now();
		Thread currentThread = Thread.currentThread();
		String threadName = currentThread.getName();
		long threadId = currentThread.threadId();
		return new TwoArgLogEvent(timeStamp, threadName, threadId, level, loggerName, message, keyValues, arg1, arg2);
	}

	public static LogEvent ofArgs(System.Logger.Level level, String loggerName, String message, KeyValues keyValues,
			Object[] args) {
		Instant timeStamp = Instant.now();
		Thread currentThread = Thread.currentThread();
		String threadName = currentThread.getName();
		long threadId = currentThread.threadId();
		return new ArrayArgLogEvent(timeStamp, threadName, threadId, level, loggerName, message, keyValues, args);
	}

	public Instant timeStamp();

	public String threadName();

	public long threadId();

	public System.Logger.Level level();

	public String loggerName();

	public void formattedMessage(StringBuilder sb);

	default void formattedMessage(Appendable a) {
		StringBuilder sb = new StringBuilder();
		formattedMessage(sb);
		try {
			a.append(sb);
		}
		catch (IOException e) {
			throw new UncheckedIOException(e);
		}
	}

	public Throwable throwable();

	public KeyValues keyValues();

	public int argCount();

	public LogEvent freeze();

	default MessageFormatType formatType() {
		return MessageFormatType.SLF4J;
	}

	public interface MessageFormatter {

		void format(StringBuilder builder, String message, Object arg1);

		void format(StringBuilder builder, String message, Object arg1, Object arg2);

		void formatArray(StringBuilder builder, String message, Object[] args);

	}

	public enum MessageFormatType implements MessageFormatter {

		SLF4J() {
			@Override
			public void format(StringBuilder builder, String message, Object arg1) {
				SLF4JMessageFormatter.format(builder, message, arg1);
			}

			@Override
			public void format(StringBuilder builder, String message, Object arg1, Object arg2) {
				SLF4JMessageFormatter.format(builder, message, arg1, arg2);
			}

			@Override
			public void formatArray(StringBuilder builder, String message, Object[] args) {
				SLF4JMessageFormatter.format(builder, message, args);
			}
		}

	}

	public interface EventCreator<LEVEL> extends MessageFormatter {

		public String loggerName();

		public System.Logger.Level translateLevel(LEVEL level);

		public KeyValues keyValues();

		default MessageFormatType formatType() {
			return MessageFormatType.SLF4J;
		}

		@Override
		default void format(StringBuilder builder, String message, Object arg1) {
			formatType().format(builder, message, arg1);
		}

		@Override
		default void format(StringBuilder builder, String message, Object arg1, Object arg2) {
			formatType().format(builder, message, arg1, arg2);
		}

		@Override
		default void formatArray(StringBuilder builder, String message, Object[] args) {
			formatType().format(builder, message, args);
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
			return LogEvent.of(sysLevel, loggerName, message, keyValues, arg1);
		}

		default LogEvent event2(LEVEL level, String message, Object arg1, Object arg2) {
			var sysLevel = translateLevel(level);
			var loggerName = loggerName();
			var keyValues = keyValues();
			return LogEvent.of(sysLevel, loggerName, message, keyValues, arg1, arg2);
		}

		default LogEvent eventArray(LEVEL level, String message, Object[] args) {
			var sysLevel = translateLevel(level);
			var loggerName = loggerName();
			var keyValues = keyValues();
			return LogEvent.ofArgs(sysLevel, loggerName, message, keyValues, args);
		}

	}

}

record OneArgLogEvent(Instant timeStamp, String threadName, long threadId, System.Logger.Level level, String loggerName,
		String message, KeyValues keyValues, @Nullable Object arg1) implements LogEvent {

	public void formattedMessage(StringBuilder sb) {
		formatType().format(sb, message, arg1);
	}

	public int argCount() {
		return 1;
	}

	public @Nullable Throwable throwable() {
		return null;
	}

	public LogEvent freeze() {
		StringBuilder sb = new StringBuilder(message.length());
		formattedMessage(sb);
		return new DefaultLogEvent(timeStamp, threadName, threadId, level, loggerName, sb.toString(),
				keyValues.freeze(), null);
	}

}

record TwoArgLogEvent(Instant timeStamp, String threadName, long threadId, System.Logger.Level level, String loggerName,
		String message, KeyValues keyValues, @Nullable Object arg1, @Nullable Object arg2) implements LogEvent {

	public void formattedMessage(StringBuilder sb) {
		formatType().format(sb, message, arg1, arg2);
	}

	public int argCount() {
		return 2;
	}

	public @Nullable Throwable throwable() {
		return null;
	}

	public LogEvent freeze() {
		StringBuilder sb = new StringBuilder(message.length());
		formattedMessage(sb);
		return new DefaultLogEvent(timeStamp, threadName, threadId, level, loggerName, sb.toString(),
				keyValues.freeze(), null);
	}
}

record ArrayArgLogEvent(Instant timeStamp, String threadName, long threadId, System.Logger.Level level,
		String loggerName, String message, KeyValues keyValues, Object[] args) implements LogEvent {

	public void formattedMessage(StringBuilder sb) {
		formatType().formatArray(sb, message, args);
	}

	public int argCount() {
		return args.length;
	}

	public @Nullable Throwable throwable() {
		return null;
	}

	public LogEvent freeze() {
		StringBuilder sb = new StringBuilder(message.length());
		formattedMessage(sb);
		return new DefaultLogEvent(timeStamp, threadName, threadId, level, loggerName, sb.toString(),
				keyValues.freeze(), null);
	}
}

record DefaultLogEvent(Instant timeStamp, String threadName, long threadId, System.Logger.Level level,
		String loggerName, String formattedMessage, KeyValues keyValues,
		@Nullable Throwable throwable) implements LogEvent {

	public int argCount() {
		return 0;
	}

	public @Nullable Throwable getThrowable() {
		return throwable();
	}

	public KeyValues getKeyValues() {
		return keyValues;
	}

	public void formattedMessage(StringBuilder sb) {
		sb.append(this.formattedMessage);
	}

	public LogEvent freeze() {
		if (keyValues instanceof MutableKeyValues mkvs) {
			return new DefaultLogEvent(timeStamp, threadName, threadId, level, loggerName, formattedMessage, mkvs,
					throwable);
		}
		return this;
	}

}

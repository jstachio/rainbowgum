package io.jstach.rainbowgum;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.lang.System.Logger.Level;
import java.time.Instant;

import org.eclipse.jdt.annotation.Nullable;

import io.jstach.rainbowgum.KeyValues.MutableKeyValues;

/**
 * A LogEvent is a container for a single call to a logger. An event should not created
 * unless a route or logger is actually enabled.
 *
 * @author agentgt
 * @apiNote LogEvent is currently sealed
 * @see LogMessageFormatter
 */
public sealed interface LogEvent {

	/**
	 * Creates a log event.
	 * @param level the logging level.
	 * @param loggerName the name of the logger which is usually a class name.
	 * @param formattedMessage the unformatted message.
	 * @param keyValues key values that come from MDC or an SLF4J Event Builder.
	 * @param throwable an exception if passed maybe <code>null</code>.
	 * @return event
	 * @see LevelResolver
	 * @apiNote the message is already assumed to be formatted as no arguments are passed.
	 */
	public static LogEvent of(System.Logger.Level level, String loggerName, String formattedMessage,
			KeyValues keyValues, @Nullable Throwable throwable) {
		Instant timeStamp = Instant.now();
		Thread currentThread = Thread.currentThread();
		String threadName = currentThread.getName();
		long threadId = currentThread.threadId();

		return new DefaultLogEvent(timeStamp, threadName, threadId, level, loggerName, formattedMessage, keyValues,
				throwable);

	}

	/**
	 * Creates a log event.
	 * @param level the logging level.
	 * @param loggerName the name of the logger which is usually a class name.
	 * @param formattedMessage the unformatted message.
	 * @param throwable an exception if passed maybe <code>null</code>.
	 * @return event
	 * @see LevelResolver
	 * @apiNote the message is already assumed to be formatted as no arguments are passed.
	 */
	static LogEvent of(System.Logger.Level level, String loggerName, String formattedMessage,
			@Nullable Throwable throwable) {
		return of(level, loggerName, formattedMessage, KeyValues.of(), throwable);
	}

	/**
	 * Creates a log event.
	 * @param level the logging level.
	 * @param loggerName the name of the logger which is usually a class name.
	 * @param message the unformatted message.
	 * @param keyValues key values that come from MDC or an SLF4J Event Builder.
	 * @param messageFormatter formatter to use for rendering a message when
	 * #{@link LogEvent#formattedMessage(StringBuilder)} is called.
	 * @param arg1 argument that will be passed to messageFormatter.
	 * @return event
	 * @see LevelResolver
	 * @see LogMessageFormatter
	 */
	public static LogEvent of(System.Logger.Level level, String loggerName, String message, KeyValues keyValues,
			LogMessageFormatter messageFormatter, @Nullable Object arg1) {
		Instant timeStamp = Instant.now();
		Thread currentThread = Thread.currentThread();
		String threadName = currentThread.getName();
		long threadId = currentThread.threadId();
		return new OneArgLogEvent(timeStamp, threadName, threadId, level, loggerName, message, keyValues,
				messageFormatter, arg1);
	}

	/**
	 * Creates a log event.
	 * @param level the logging level.
	 * @param loggerName the name of the logger which is usually a class name.
	 * @param message the unformatted message.
	 * @param keyValues key values that come from MDC or an SLF4J Event Builder.
	 * @param messageFormatter formatter to use for rendering a message when
	 * #{@link LogEvent#formattedMessage(StringBuilder)} is called.
	 * @param arg1 argument that will be passed to messageFormatter.
	 * @param arg2 argument that will be passed to messageFormatter.
	 * @return event
	 * @see LevelResolver
	 * @see LogMessageFormatter
	 */
	public static LogEvent of(System.Logger.Level level, String loggerName, String message, KeyValues keyValues,
			LogMessageFormatter messageFormatter, @Nullable Object arg1, @Nullable Object arg2) {
		Instant timeStamp = Instant.now();
		Thread currentThread = Thread.currentThread();
		String threadName = currentThread.getName();
		long threadId = currentThread.threadId();
		return new TwoArgLogEvent(timeStamp, threadName, threadId, level, loggerName, message, keyValues,
				messageFormatter, arg1, arg2);
	}

	/**
	 * Creates a log event.
	 * @param level the logging level.
	 * @param loggerName the name of the logger which is usually a class name.
	 * @param message the unformatted message.
	 * @param keyValues key values that come from MDC or an SLF4J Event Builder.
	 * @param messageFormatter formatter to use for rendering a message when
	 * #{@link LogEvent#formattedMessage(StringBuilder)} is called.
	 * @param args an array of arguments that will be passed to messageFormatter. The
	 * contents maybe null elements but the array itself should not be null.
	 * @return event
	 * @see LevelResolver
	 * @see LogMessageFormatter
	 */
	public static LogEvent ofArgs(System.Logger.Level level, String loggerName, String message, KeyValues keyValues,
			LogMessageFormatter messageFormatter, @Nullable Object[] args) {
		Instant timeStamp = Instant.now();
		Thread currentThread = Thread.currentThread();
		String threadName = currentThread.getName();
		long threadId = currentThread.threadId();
		return new ArrayArgLogEvent(timeStamp, threadName, threadId, level, loggerName, message, keyValues,
				messageFormatter, args);
	}

	/**
	 * Timestamp when the event was created.
	 * @return instant when the event was created.
	 */
	public Instant timestamp();

	/**
	 * Name of the thread.
	 * @return thread name.
	 * @apiNote this maybe empty and often is if virtual threads are used.
	 */
	public String threadName();

	/**
	 * Thread id.
	 * @return thread id.
	 */
	public long threadId();

	/**
	 * The logging level. {@linkplain System.Logger.Level#ALL} and
	 * {@linkplain System.Logger.Level#OFF} should not be returned as they have special
	 * meaning.
	 * @return level.
	 */
	public System.Logger.Level level();

	/**
	 * Name of logger.
	 * @return name of logger.
	 */
	public String loggerName();

	/**
	 * Appends the formatted message.
	 * @param sb string builder to use.
	 * @see LogMessageFormatter
	 */
	public void formattedMessage(StringBuilder sb);

	/**
	 * Appends the formatted message.
	 * @param a appendable to append to.
	 * @see LogMessageFormatter
	 * @throws UncheckedIOException if an IOException is thrown by the appendable.
	 */
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

	/**
	 * Throwable at the time of the event passed from the logger.
	 * @return if the event does not have a throwable <code>null</code> will be returned.
	 */
	public @Nullable Throwable throwable();

	/**
	 * Key values that usually come from MDC or an SLF4J Event Builder.
	 * @return key values.
	 */
	public KeyValues keyValues();

	/**
	 * Freeze will return a LogEvent that is safe to use in a different thread. Usually
	 * this entails copying the data or checking if it is already immutable. Freeze should
	 * be called before passing an event to an {@link LogPublisher.AsyncLogPublisher}.
	 * @return thread safe log event.
	 */
	public LogEvent freeze();

}

enum EmptyLogEvent implements LogEvent {

	INFO() {
		public Level level() {
			return Level.INFO;
		}
	},
	DEBUG() {
		public Level level() {
			return Level.DEBUG;
		}
	};

	@Override
	public Instant timestamp() {
		return null;
	}

	@Override
	public String threadName() {
		return null;
	}

	@Override
	public long threadId() {
		return 0;
	}

	@Override
	public String loggerName() {
		return "";
	}

	@Override
	public void formattedMessage(StringBuilder sb) {

	}

	@Override
	public Throwable throwable() {
		return null;
	}

	@Override
	public KeyValues keyValues() {
		return KeyValues.of();
	}

	public int argCount() {
		return 0;
	}

	@Override
	public LogEvent freeze() {
		return this;
	}

}

record OneArgLogEvent(Instant timestamp, String threadName, long threadId, System.Logger.Level level, String loggerName,
		String message, KeyValues keyValues, LogMessageFormatter messageFormatter,
		@Nullable Object arg1) implements LogEvent {

	public void formattedMessage(StringBuilder sb) {
		messageFormatter.format(sb, message, arg1);
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
		return new DefaultLogEvent(timestamp, threadName, threadId, level, loggerName, sb.toString(),
				keyValues.freeze(), null);
	}

}

record TwoArgLogEvent(Instant timestamp, String threadName, long threadId, System.Logger.Level level, String loggerName,
		String message, KeyValues keyValues, LogMessageFormatter messageFormatter, @Nullable Object arg1,
		@Nullable Object arg2) implements LogEvent {

	public void formattedMessage(StringBuilder sb) {
		messageFormatter.format(sb, message, arg1, arg2);
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
		return new DefaultLogEvent(timestamp, threadName, threadId, level, loggerName, sb.toString(),
				keyValues.freeze(), null);
	}
}

record ArrayArgLogEvent(Instant timestamp, String threadName, long threadId, System.Logger.Level level,
		String loggerName, String message, KeyValues keyValues, LogMessageFormatter messageFormatter,
		Object[] args) implements LogEvent {

	public void formattedMessage(StringBuilder sb) {
		messageFormatter.formatArray(sb, message, args);
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
		return new DefaultLogEvent(timestamp, threadName, threadId, level, loggerName, sb.toString(),
				keyValues.freeze(), null);
	}
}

record DefaultLogEvent(Instant timestamp, String threadName, long threadId, System.Logger.Level level,
		String loggerName, String formattedMessage, KeyValues keyValues,
		@Nullable Throwable throwable) implements LogEvent {

	public int argCount() {
		return 0;
	}

	public KeyValues getKeyValues() {
		return keyValues;
	}

	public void formattedMessage(StringBuilder sb) {
		sb.append(this.formattedMessage);
	}

	public LogEvent freeze() {
		if (keyValues instanceof MutableKeyValues mkvs) {
			return new DefaultLogEvent(timestamp, threadName, threadId, level, loggerName, formattedMessage, mkvs,
					throwable);
		}
		return this;
	}

}

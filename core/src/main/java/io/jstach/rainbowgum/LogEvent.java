package io.jstach.rainbowgum;

import java.lang.StackWalker.Option;
import java.lang.StackWalker.StackFrame;
import java.lang.System.Logger.Level;
import java.time.Instant;
import java.util.List;
import java.util.Objects;

import org.eclipse.jdt.annotation.Nullable;

import io.jstach.rainbowgum.KeyValues.MutableKeyValues;
import io.jstach.rainbowgum.LogEvent.Caller;

/**
 * A LogEvent is a container for a single call to a logger. An event should not be created
 * unless a route or logger is actually enabled.
 *
 * @author agentgt
 * @apiNote LogEvent is currently sealed. The reason there are so many static creation
 * methods is for optimization purposes because other than actual outputting creating
 * events is generally the most expensive operation (mostly in terms of memory) a logging
 * system does.
 * @see LogMessageFormatter
 */
public sealed interface LogEvent {

	/**
	 * Creates a log event.
	 * @param timestamp time of event
	 * @param threadName or empty string
	 * @param threadId thread id or 0 if that cannot be resolved.
	 * @param level the logging level.
	 * @param loggerName the name of the logger which is usually a class name.
	 * @param formattedMessage the unformatted message.
	 * @param keyValues key values that come from MDC or an SLF4J Event Builder.
	 * @param throwable an exception if passed maybe <code>null</code>.
	 * @return event
	 * @see LevelResolver
	 * @apiNote the message is already assumed to be formatted as no arguments are passed.
	 */
	public static LogEvent of(Instant timestamp, String threadName, long threadId, System.Logger.Level level,
			String loggerName, @Nullable String formattedMessage, KeyValues keyValues, @Nullable Throwable throwable) {
		return new DefaultLogEvent(timestamp, threadName, threadId, level, loggerName, formattedMessage, keyValues,
				throwable);
	}

	/**
	 * Creates a log event.
	 * @param timestamp time of event
	 * @param threadName or empty string
	 * @param threadId thread id or 0 if that cannot be resolved.
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
	public static LogEvent ofOneArg(Instant timestamp, String threadName, long threadId, System.Logger.Level level,
			String loggerName, @Nullable String message, KeyValues keyValues, LogMessageFormatter messageFormatter,
			@Nullable Object arg1) {
		if (arg1 instanceof Throwable t) {
			return new DefaultLogEvent(timestamp, threadName, threadId, level, loggerName, message, keyValues, t);
		}
		if (message == null) {
			return new DefaultLogEvent(timestamp, threadName, threadId, level, loggerName, message, keyValues, null);

		}
		return new OneArgLogEvent(timestamp, threadName, threadId, level, loggerName, message, keyValues,
				messageFormatter, null, arg1);
	}

	/**
	 * Creates a log event.
	 * @param timestamp time of event
	 * @param threadName or empty string
	 * @param threadId thread id or 0 if that cannot be resolved.
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
	public static LogEvent ofTwoArgs(Instant timestamp, String threadName, long threadId, System.Logger.Level level,
			String loggerName, @Nullable String message, KeyValues keyValues, LogMessageFormatter messageFormatter,
			@Nullable Object arg1, @Nullable Object arg2) {
		if (arg2 instanceof Throwable t) {
			if (message == null) {
				return new DefaultLogEvent(timestamp, threadName, threadId, level, loggerName, message, keyValues, t);
			}
			return new OneArgLogEvent(timestamp, threadName, threadId, level, loggerName, message, keyValues,
					messageFormatter, t, arg1);
		}
		if (message == null) {
			return new DefaultLogEvent(timestamp, threadName, threadId, level, loggerName, message, keyValues, null);
		}
		return new TwoArgLogEvent(timestamp, threadName, threadId, level, loggerName, message, keyValues,
				messageFormatter, null, arg1, arg2);
	}

	/**
	 * Creates a log event with everything specified.
	 * @param timestamp time of event
	 * @param threadName or empty string
	 * @param threadId thread id or 0 if that cannot be resolved.
	 * @param level the logging level.
	 * @param loggerName the name of the logger which is usually a class name.
	 * @param message the unformatted message.
	 * @param keyValues key values that come from MDC or an SLF4J Event Builder.
	 * @param throwable or <code>null</code>.
	 * @param messageFormatter formatter to use for rendering a message when
	 * #{@link LogEvent#formattedMessage(StringBuilder)} is called.
	 * @param args an array of arguments that will be passed to messageFormatter. The
	 * contents maybe null elements but the array itself should not be null.
	 * @return event
	 * @see LevelResolver
	 * @see LogMessageFormatter
	 */
	public static LogEvent ofAll(Instant timestamp, String threadName, long threadId, System.Logger.Level level,
			String loggerName, @Nullable String message, KeyValues keyValues, @Nullable Throwable throwable,
			LogMessageFormatter messageFormatter, @SuppressWarnings("exports") @Nullable List<@Nullable Object> args) {

		if (args == null) {
			return new DefaultLogEvent(timestamp, threadName, threadId, level, loggerName, message, keyValues,
					throwable);
		}
		int length = args.size();
		if (throwable == null && length > 0 && args.get(length - 1) instanceof Throwable t) {
			throwable = t;
			length = length - 1;
		}
		if (message == null) {
			return new DefaultLogEvent(timestamp, threadName, threadId, level, loggerName, message, keyValues,
					throwable);
		}

		return switch (length) {
			case 0 ->
				new DefaultLogEvent(timestamp, threadName, threadId, level, loggerName, message, keyValues, throwable);
			case 1 -> new OneArgLogEvent(timestamp, threadName, threadId, level, loggerName, message, keyValues,
					messageFormatter, throwable, args.get(0));
			case 2 -> new TwoArgLogEvent(timestamp, threadName, threadId, level, loggerName, message, keyValues,
					messageFormatter, throwable, args.get(0), args.get(1));
			default -> new ArrayArgLogEvent(timestamp, threadName, threadId, level, loggerName, message, keyValues,
					messageFormatter, throwable, args.toArray(), length);
		};
	}

	/**
	 * Creates a log event with everything specified.
	 * @param timestamp time of event
	 * @param threadName or empty string
	 * @param threadId thread id or 0 if that cannot be resolved.
	 * @param level the logging level.
	 * @param loggerName the name of the logger which is usually a class name.
	 * @param message the unformatted message.
	 * @param keyValues key values that come from MDC or an SLF4J Event Builder.
	 * @param throwable or <code>null</code>.
	 * @param messageFormatter formatter to use for rendering a message when
	 * #{@link LogEvent#formattedMessage(StringBuilder)} is called.
	 * @param args an array of arguments that will be passed to messageFormatter. The
	 * contents maybe null elements but the array itself should not be null.
	 * @return event
	 * @see LevelResolver
	 * @see LogMessageFormatter
	 */
	public static LogEvent ofAll(Instant timestamp, String threadName, long threadId, System.Logger.Level level,
			String loggerName, @Nullable String message, KeyValues keyValues, @Nullable Throwable throwable,
			LogMessageFormatter messageFormatter, @SuppressWarnings("exports") @Nullable Object @Nullable [] args) {

		if (args == null) {
			return new DefaultLogEvent(timestamp, threadName, threadId, level, loggerName, message, keyValues,
					throwable);
		}
		int length = args.length;
		if (throwable == null && length > 0 && args[length - 1] instanceof Throwable t) {
			throwable = t;
			length = length - 1;
		}
		if (message == null) {
			return new DefaultLogEvent(timestamp, threadName, threadId, level, loggerName, message, keyValues,
					throwable);
		}

		return switch (length) {
			case 0 ->
				new DefaultLogEvent(timestamp, threadName, threadId, level, loggerName, message, keyValues, throwable);
			case 1 -> new OneArgLogEvent(timestamp, threadName, threadId, level, loggerName, message, keyValues,
					messageFormatter, throwable, args[0]);
			case 2 -> new TwoArgLogEvent(timestamp, threadName, threadId, level, loggerName, message, keyValues,
					messageFormatter, throwable, args[0], args[1]);
			default -> new ArrayArgLogEvent(timestamp, threadName, threadId, level, loggerName, message, keyValues,
					messageFormatter, throwable, args, length);
		};
	}

	/**
	 * Creates a new event with the caller info attached. <em> This method always returns
	 * a new event does not check if the original has caller info. </em>
	 * @param event original event.
	 * @param caller caller info.
	 * @return new log event.
	 */
	public static LogEvent withCaller(LogEvent event, Caller caller) {
		return new StackFrameLogEvent(event, caller);
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
	 * The logging level. {@linkplain java.lang.System.Logger.Level#ALL} and
	 * {@linkplain java.lang.System.Logger.Level#OFF} should not be returned as they have
	 * special meaning.
	 * @return level.
	 */
	public System.Logger.Level level();

	/**
	 * Name of logger.
	 * @return name of logger.
	 */
	public String loggerName();

	/**
	 * Unformatted message.
	 * @return unformatted message
	 * @see #formattedMessage(StringBuilder)
	 */
	public String message();

	/**
	 * Unformatted message or <code>null</code> if no message was passed into the event.
	 * @return unformatted message or <code>null</code>.
	 * @see #formattedMessage(StringBuilder)
	 */
	default @Nullable String messageOrNull() {
		if (hasMessage()) {
			return message();
		}
		return null;
	}

	/**
	 * If the event has a message or not. Because the return of {@link #message()} is non
	 * null there is no way to detect that a message is missing.
	 * @return <code>true</code> if this event has a message.
	 */
	default public boolean hasMessage() {
		return true;
	}

	/**
	 * Appends the formatted message.
	 * @param sb string builder to use.
	 * @see LogMessageFormatter
	 */
	public void formattedMessage(StringBuilder sb);

	/**
	 * Appends the formatted message.
	 * @param sb string builder to use.
	 * @param fallbackMessage string to use if the message was never set.
	 * @see LogMessageFormatter
	 * @see #hasMessage()
	 */
	default void formattedMessage(StringBuilder sb, String fallbackMessage) {
		if (hasMessage()) {
			formattedMessage(sb);
		}
		else {
			sb.append(fallbackMessage);
		}
	}

	/**
	 * Throwable at the time of the event passed from the logger.
	 * @return if the event does not have a throwable <code>null</code> will be returned.
	 */
	public @Nullable Throwable throwableOrNull();

	/**
	 * Key values that usually come from MDC or an SLF4J Event Builder.
	 * @return key values.
	 */
	public KeyValues keyValues();

	/**
	 * Returns info about caller or <code>null</code> if not supported.
	 * @return caller info
	 */
	default @Nullable Caller callerOrNull() {
		return null;
	}

	/**
	 * Freeze will return a LogEvent that is safe to use in a different thread. Usually
	 * this entails copying the data or checking if it is already immutable. Freeze should
	 * be called before passing an event to an {@link LogPublisher.AsyncLogPublisher}.
	 * @return thread safe log event.
	 */
	public LogEvent freeze();

	/**
	 * Freeze and replace with the given timestamp.
	 * @param timestamp instant to replace timestamp in this.
	 * @return a copy of this with the given timestamp.
	 */
	public LogEvent freeze(Instant timestamp);

	/**
	 * Caller info usually derived from Stack walking.
	 */
	public sealed interface Caller {

		/**
		 * Creates caller info from a stack frame.
		 * @param stackFrame stack frame must have
		 * {@link java.lang.StackWalker.Option#RETAIN_CLASS_REFERENCE}.
		 * @return caller info.
		 */
		public static Caller of(StackFrame stackFrame) {
			return new StackFrameCallerInfo(stackFrame);
		}

		/**
		 * Returns caller from a certain depth or <code>null</code>
		 * @param depth how deep in the stack to pull stack frame.
		 * @return caller or <code>null</code>.
		 */
		public static @Nullable Caller ofDepthOrNull(int depth) {
			return StackFrameCallerInfo.stackWalker.<@Nullable Caller>walk(
					s -> s.skip(depth + 1).limit(1).map(f -> Caller.of(f)).findFirst().orElse(null));
		}

		/**
		 * See {@link StackFrame#getClassName()}.
		 * @return class name.
		 */
		public String className();

		/**
		 * See {@link StackFrame#getFileName()}.
		 * @return file name.
		 */
		public @Nullable String fileNameOrNull();

		/**
		 * See {@link StackFrame#getLineNumber()}.
		 * @return line number.
		 */
		public int lineNumber();

		/**
		 * See {@link StackFrame#getMethodName()}.
		 * @return method name.
		 */
		public String methodName();

		/**
		 * Make the caller info immutable.
		 * @return immutable caller info and if this is already immutable return this.
		 */
		public Caller freeze();

		/**
		 * Convenience toString for caller.
		 * @param caller if caller is <code>null</code> the string "null" will be
		 * returned.
		 * @return string representation.
		 */
		public static String toString(@Nullable Caller caller) {
			/*
			 * TODO maybe move this to formatters?
			 */
			if (caller == null)
				return "null";
			return caller.fileNameOrNull() + ":" + caller.lineNumber() + "/" + caller.className() + "."
					+ caller.methodName();
		}

	}

}

record FrozenCallerInfo(String className, @Nullable String fileNameOrNull, int lineNumber,
		String methodName) implements LogEvent.Caller {
	@Override
	public Caller freeze() {
		return this;
	}
}

record StackFrameCallerInfo(StackFrame stackFrame) implements LogEvent.Caller {

	static final StackWalker stackWalker = StackWalker.getInstance(Option.RETAIN_CLASS_REFERENCE);

	@Override
	public String className() {
		return stackFrame.getClassName();
	}

	@Override
	public @Nullable String fileNameOrNull() {
		/*
		 * TODO checker did not know file name is null.
		 */
		return stackFrame.getFileName();
	}

	@Override
	public int lineNumber() {
		return stackFrame.getLineNumber();
	}

	@Override
	public String methodName() {
		return stackFrame.getMethodName();
	}

	@Override
	public Caller freeze() {
		return new FrozenCallerInfo(className(), fileNameOrNull(), lineNumber(), methodName());
	}

}

record OneArgLogEvent(Instant timestamp, String threadName, long threadId, System.Logger.Level level, String loggerName,
		String message, KeyValues keyValues, LogMessageFormatter messageFormatter, @Nullable Throwable throwableOrNull,
		@Nullable Object arg1) implements LogEvent {

	OneArgLogEvent {
		Objects.requireNonNull(timestamp, "timestamp");
		Objects.requireNonNull(threadName, "threadName");
		Objects.requireNonNull(level, "level");
		Objects.requireNonNull(loggerName, "loggerName");
		Objects.requireNonNull(message, "message");
		Objects.requireNonNull(keyValues, "keyValues");
		Objects.requireNonNull(messageFormatter, "messageFormatter");
	}

	@Override
	public void formattedMessage(StringBuilder sb) {
		messageFormatter.format(sb, message, arg1);
	}

	@Override
	public LogEvent freeze() {
		return freeze(timestamp);
	}

	@Override
	public LogEvent freeze(Instant timestamp) {
		StringBuilder sb = new StringBuilder(message.length());
		formattedMessage(sb);
		return new DefaultLogEvent(timestamp, threadName, threadId, level, loggerName, sb.toString(),
				keyValues.freeze(), throwableOrNull);
	}

}

record TwoArgLogEvent(Instant timestamp, String threadName, long threadId, System.Logger.Level level, String loggerName,
		String message, KeyValues keyValues, LogMessageFormatter messageFormatter, @Nullable Throwable throwableOrNull,
		@Nullable Object arg1, @Nullable Object arg2) implements LogEvent {

	TwoArgLogEvent {
		Objects.requireNonNull(timestamp, "timestamp");
		Objects.requireNonNull(threadName, "threadName");
		Objects.requireNonNull(level, "level");
		Objects.requireNonNull(loggerName, "loggerName");
		Objects.requireNonNull(message, "message");
		Objects.requireNonNull(keyValues, "keyValues");
		Objects.requireNonNull(messageFormatter, "messageFormatter");
	}

	@Override
	public void formattedMessage(StringBuilder sb) {
		messageFormatter.format(sb, message, arg1, arg2);
	}

	@Override
	public LogEvent freeze() {
		return freeze(timestamp);
	}

	@Override
	public LogEvent freeze(Instant timestamp) {
		StringBuilder sb = new StringBuilder(message.length());
		formattedMessage(sb);
		return new DefaultLogEvent(timestamp, threadName, threadId, level, loggerName, sb.toString(),
				keyValues.freeze(), throwableOrNull);
	}
}

@SuppressWarnings("ArrayRecordComponent")
record ArrayArgLogEvent(Instant timestamp, String threadName, long threadId, System.Logger.Level level,
		String loggerName, String message, KeyValues keyValues, LogMessageFormatter messageFormatter,
		@Nullable Throwable throwableOrNull, @Nullable Object[] args, int length) implements LogEvent {

	ArrayArgLogEvent {
		Objects.requireNonNull(timestamp, "timestamp");
		Objects.requireNonNull(threadName, "threadName");
		Objects.requireNonNull(level, "level");
		Objects.requireNonNull(loggerName, "loggerName");
		Objects.requireNonNull(message, "message");
		Objects.requireNonNull(keyValues, "keyValues");
		Objects.requireNonNull(messageFormatter, "messageFormatter");
	}

	@Override
	public void formattedMessage(StringBuilder sb) {
		messageFormatter.formatArray(sb, message, args, length);
	}

	public int argCount() {
		return args.length;
	}

	@Override
	public LogEvent freeze() {
		return freeze(timestamp);
	}

	@Override
	public LogEvent freeze(Instant timestamp) {
		StringBuilder sb = new StringBuilder(message.length());
		formattedMessage(sb);
		return new DefaultLogEvent(timestamp, threadName, threadId, level, loggerName, sb.toString(),
				keyValues.freeze(), throwableOrNull);
	}

}

record DefaultLogEvent(Instant timestamp, String threadName, long threadId, System.Logger.Level level,
		String loggerName, @Nullable String formattedMessage, KeyValues keyValues,
		@Nullable Throwable throwableOrNull) implements LogEvent {

	DefaultLogEvent {
		Objects.requireNonNull(timestamp, "timestamp");
		Objects.requireNonNull(threadName, "threadName");
		Objects.requireNonNull(level, "level");
		Objects.requireNonNull(loggerName, "loggerName");
		Objects.requireNonNull(keyValues, "keyValues");
	}

	@Override
	public void formattedMessage(StringBuilder sb) {
		sb.append(this.formattedMessage);
	}

	@Override
	public String message() {
		return "" + this.formattedMessage;
	}

	@Override
	public @Nullable String messageOrNull() {
		return this.formattedMessage;
	}

	@Override
	public boolean hasMessage() {
		return this.formattedMessage != null;
	}

	@Override
	public LogEvent freeze() {
		if (keyValues instanceof MutableKeyValues mkvs) {
			return new DefaultLogEvent(timestamp, threadName, threadId, level, loggerName, formattedMessage,
					mkvs.freeze(), throwableOrNull);
		}
		return this;
	}

	@Override
	public LogEvent freeze(Instant timestamp) {
		if (keyValues instanceof MutableKeyValues mkvs) {
			return new DefaultLogEvent(timestamp, threadName, threadId, level, loggerName, formattedMessage,
					mkvs.freeze(), throwableOrNull);
		}
		return new DefaultLogEvent(timestamp, threadName, threadId, level, loggerName, formattedMessage, keyValues,
				throwableOrNull);
	}

}

record StackFrameLogEvent(LogEvent event, Caller callerOrNull) implements LogEvent {

	@Override
	public Instant timestamp() {
		return event.timestamp();
	}

	@Override
	public String threadName() {
		return event.threadName();
	}

	@Override
	public long threadId() {
		return event.threadId();
	}

	@Override
	public Level level() {
		return event.level();
	}

	@Override
	public String loggerName() {
		return event.loggerName();
	}

	@Override
	public String message() {
		return event.message();
	}

	@Override
	public void formattedMessage(StringBuilder sb) {
		event.formattedMessage(sb);

	}

	@Override
	public @Nullable Throwable throwableOrNull() {
		return event.throwableOrNull();
	}

	@Override
	public KeyValues keyValues() {
		return event.keyValues();
	}

	@Override
	@SuppressWarnings("ReferenceEquality")
	public LogEvent freeze() {
		var e = event.freeze();
		var info = callerOrNull.freeze();
		if (e == event && info == callerOrNull) {
			return this;
		}

		return new StackFrameLogEvent(e, info);
	}

	@Override
	@SuppressWarnings("ReferenceEquality")
	public LogEvent freeze(Instant timestamp) {
		var e = event.freeze(timestamp);
		var info = callerOrNull.freeze();
		if (e == event && info == callerOrNull) {
			return this;
		}
		return new StackFrameLogEvent(e, info);
	}

}

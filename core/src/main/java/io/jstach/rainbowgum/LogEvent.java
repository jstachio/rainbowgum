package io.jstach.rainbowgum;

import java.lang.StackWalker.Option;
import java.lang.StackWalker.StackFrame;
import java.lang.System.Logger.Level;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

import org.eclipse.jdt.annotation.Nullable;

import io.jstach.rainbowgum.KeyValues.MutableKeyValues;
import io.jstach.rainbowgum.LogEvent.Builder;
import io.jstach.rainbowgum.LogEvent.Caller;
import io.jstach.rainbowgum.LogRouter.Router;

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
		if (arg1 instanceof Throwable t) {
			return new DefaultLogEvent(timeStamp, threadName, threadId, level, loggerName, message, keyValues, t);
		}
		return new OneArgLogEvent(timeStamp, threadName, threadId, level, loggerName, message, keyValues,
				messageFormatter, null, arg1);
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
		if (arg2 instanceof Throwable t) {
			return new OneArgLogEvent(timeStamp, threadName, threadId, level, loggerName, message, keyValues,
					messageFormatter, t, arg1);
		}
		return new TwoArgLogEvent(timeStamp, threadName, threadId, level, loggerName, message, keyValues,
				messageFormatter, null, arg1, arg2);
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
		return ofAll(timeStamp, threadName, threadId, level, loggerName, message, keyValues, null, messageFormatter,
				args);
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
			String loggerName, String message, KeyValues keyValues, @Nullable Throwable throwable,
			LogMessageFormatter messageFormatter,
			//TODO NullAway bug
			@SuppressWarnings({"exports", "NullAway"}) @Nullable List<@Nullable Object> args) {

		if (args == null) {
			return new DefaultLogEvent(timestamp, threadName, threadId, level, loggerName, message, keyValues,
					throwable);
		}
		int length = args.size();
		if (throwable == null && length > 0 && args.get(length - 1) instanceof Throwable t) {
			throwable = t;
			length = length - 1;
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
			String loggerName, String message, KeyValues keyValues, @Nullable Throwable throwable,
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
	 * Appends the formatted message.
	 * @param sb string builder to use.
	 * @see LogMessageFormatter
	 */
	public void formattedMessage(StringBuilder sb);

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
	 * A builder to create events by calling {@link Router#eventBuilder(String, Level)}.
	 * <strong>{@link #log()}</strong> should be called at the very end otherwise the
	 * event will not be logged. <strong> Because of potential future optimizations (see
	 * API note) It is best to assume this builder returns a different builder on every
	 * setter call even if it may not currently! </strong>
	 *
	 * @apiNote unless the builder gets marked for escape analysis the static
	 * {@link LogEvent} factory methods are more likely to be superior in performance. EA
	 * is tricky business and often requires the object be small enough and should not
	 * leave the method it is constructed in but the JDK is continuously getting better
	 * optimizing immutable objects. Thus it is best to assume this builder will return a
	 * different builder on every call.
	 */
	public sealed interface Builder {

		/**
		 * Timestamp when the event was created.
		 * @param timestamp time the event happened.
		 * @return this.
		 */
		public Builder timestamp(Instant timestamp);

		/**
		 * Add an argument to the event being built.
		 * @param argSupplier supplier will be called immediately if this event is to be
		 * logged.
		 * @return this.
		 */
		Builder arg(Supplier<?> argSupplier);

		/**
		 * Add an argument to the event being built.
		 * @param arg maybe null.
		 * @return this.
		 */
		@SuppressWarnings("NullAway") // TODO NullAway bug
		Builder arg(@Nullable Object arg);

		/**
		 * Sets the message formatter which interpolates argument place holders.
		 * @param messageFormatter formatter if not set
		 * {@link LogMessageFormatter.StandardMessageFormatter#SLF4J} will be used.
		 * @return this.
		 */
		public Builder messageFormatter(LogMessageFormatter messageFormatter);

		/**
		 * Name of the thread.
		 * @param threadName name of thread.
		 * @return this.
		 * @apiNote this maybe empty and often is if virtual threads are used.
		 */
		public Builder threadName(String threadName);

		/**
		 * Thread id.
		 * @param threadId {@link Thread#getId()}.
		 * @return this.
		 */
		public Builder threadId(long threadId);

		/**
		 * Unformatted message.
		 * @param message unformatted message.
		 * @return unformatted message
		 */
		public Builder message(String message);

		/**
		 * Throwable at the time of the event passed from the logger.
		 * @param throwable exception at the time of the event.
		 * @return if the event does not have a throwable <code>null</code> will be
		 * returned.
		 */
		public Builder throwable(@Nullable Throwable throwable);

		/**
		 * Key values that usually come from MDC or an SLF4J Event Builder.
		 * @param keyValues will use the passed in key values for the event.
		 * @return key values.
		 */
		public Builder keyValues(KeyValues keyValues);

		/**
		 * Adds caller info.
		 * @param caller caller maybe <code>null</code>.
		 * @return this.
		 */
		public Builder caller(@Nullable Caller caller);

		/**
		 * Will log the event with the current values.
		 * @return true if accepted.
		 */
		public boolean log();

		/**
		 * Will generate the event if the router can accept otherwise <code>null</code>.
		 * @return event or <code>null</code>.
		 * @apiNote The event is not logged but just created if possible if logging is
		 * desired use {@link #log()} or check if the return is not <code>null</code> and
		 * then log.
		 */
		public @Nullable LogEvent eventOrNull();

	}

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

final class LogEventBuilder implements LogEvent.Builder {

	private final LogEventLogger logger;

	private final Level level;

	private final String loggerName;

	private Instant timestamp = Instant.now();

	private String threadName = Thread.currentThread().getName();

	private long threadId = Thread.currentThread().threadId();

	private String message = "";

	private @Nullable Throwable throwable;

	private KeyValues keyValues = KeyValues.of();

	@SuppressWarnings("NullAway") // TODO NullAway bug
	private @Nullable List<@Nullable Object> args = null;

	private LogMessageFormatter messageFormatter = LogMessageFormatter.StandardMessageFormatter.SLF4J;

	private @Nullable Caller caller = null;

	LogEventBuilder(LogEventLogger logger, Level level, String loggerName) {
		super();
		this.logger = logger;
		this.level = level;
		this.loggerName = loggerName;
	}

	@Override
	public Builder timestamp(Instant timestamp) {
		this.timestamp = timestamp;
		return this;
	}

	@Override
	public Builder threadName(String threadName) {
		this.threadName = threadName;
		return this;
	}

	@Override
	public Builder threadId(long threadId) {
		this.threadId = threadId;
		return this;
	}

	@Override
	public Builder message(String message) {
		this.message = message;
		return this;
	}

	@Override
	public Builder messageFormatter(LogMessageFormatter messageFormatter) {
		this.messageFormatter = messageFormatter;
		return this;
	}

	@Override
	public Builder throwable(@Nullable Throwable throwable) {
		this.throwable = throwable;
		return this;
	}

	@Override
	public Builder keyValues(KeyValues keyValues) {
		this.keyValues = keyValues;
		return this;
	}

	@Override
	public Builder arg(@Nullable Object arg) {
		var list = this.args;
		if (list == null) {
			list = this.args = new ArrayList<>();
		}
		list.add(arg);
		return this;
	}

	@Override
	public Builder arg(Supplier<?> argSupplier) {
		return arg(argSupplier.get());
	}

	@Override
	public Builder caller(@Nullable Caller caller) {
		this.caller = caller;
		return this;
	}

	@Override
	public boolean log() {
		var event = eventOrNull();
		logger.log(event);
		return true;
	}

	@Override
	public LogEvent eventOrNull() {
		var event = LogEvent.ofAll(timestamp, threadName, threadId, level, loggerName, message, keyValues, throwable,
				messageFormatter, this.args);
		var c = caller;
		if (c != null) {
			return LogEvent.withCaller(event, c);
		}
		return event;
	}

}

enum NoOpLogEventBuilder implements LogEvent.Builder {

	NOOP;

	@Override
	public Builder timestamp(Instant timestamp) {
		return this;
	}

	@Override
	public Builder threadName(String threadName) {
		return this;
	}

	@Override
	public Builder threadId(long threadId) {
		return this;
	}

	@Override
	public Builder message(String message) {
		return this;
	}

	@Override
	public Builder throwable(@Nullable Throwable throwable) {
		return this;
	}

	@Override
	public Builder keyValues(KeyValues keyValues) {
		return this;
	}

	@Override
	public Builder messageFormatter(LogMessageFormatter messageFormatter) {
		return this;
	}

	@Override
	public boolean log() {
		return false;
	}

	@Override
	public @Nullable LogEvent eventOrNull() {
		return null;
	}

	@Override
	public Builder arg(Supplier<?> argSupplier) {
		return this;
	}

	@Override
	public Builder arg(@Nullable Object arg) {
		return this;
	}

	@Override
	public Builder caller(@Nullable Caller caller) {
		return this;
	}

}

record OneArgLogEvent(Instant timestamp, String threadName, long threadId, System.Logger.Level level, String loggerName,
		String message, KeyValues keyValues, LogMessageFormatter messageFormatter, @Nullable Throwable throwableOrNull,
		@Nullable Object arg1) implements LogEvent {

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

record ArrayArgLogEvent(Instant timestamp, String threadName, long threadId, System.Logger.Level level,
		String loggerName, String message, KeyValues keyValues, LogMessageFormatter messageFormatter,
		@Nullable Throwable throwableOrNull, @Nullable Object[] args, int length) implements LogEvent {

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
		String loggerName, String formattedMessage, KeyValues keyValues,
		@Nullable Throwable throwableOrNull) implements LogEvent {

	@Override
	public void formattedMessage(StringBuilder sb) {
		sb.append(this.formattedMessage);
	}

	@Override
	public String message() {
		return this.formattedMessage;
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
	public LogEvent freeze() {
		var e = event.freeze();
		var info = callerOrNull.freeze();
		if (e == event && info == callerOrNull) {
			return this;
		}

		return new StackFrameLogEvent(e, info);
	}

	@Override
	public LogEvent freeze(Instant timestamp) {
		var e = event.freeze(timestamp);
		var info = callerOrNull.freeze();
		if (e == event && info == callerOrNull) {
			return this;
		}
		return new StackFrameLogEvent(e, info);
	}

}

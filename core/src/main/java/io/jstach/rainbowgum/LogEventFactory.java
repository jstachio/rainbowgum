package io.jstach.rainbowgum;

import java.lang.System.Logger.Level;
import java.time.Instant;

import org.eclipse.jdt.annotation.Nullable;

/**
 * A dynamic version of {@link LogEvent}'s static "<code>of</code>" factory methods - same
 * method shapes (level, message, ...), but {@link #timestamp()}, {@link #threadName()},
 * {@link #threadId()} and {@link #loggerName()} are resolved through overridable methods
 * instead of being passed in on every call/pulled from
 * {@link Instant#now()}/{@link Thread#currentThread()} inline the way those static
 * methods do. Those static methods are unaffected for now; this is a first step toward
 * eventually replacing them.
 * <p>
 * Not being able to override thread name/thread id/timestamp is a real limitation for
 * anything that needs deterministic events not tied to whichever real thread happens to
 * construct them - tests especially, but possibly other reasons later. A test-specific
 * subclass overriding those methods once gets an entire family of event-construction
 * methods that are deterministic, rather than needing to thread fixed values through
 * every call site.
 * <p>
 * {@link #loggerName()} is abstract rather than defaulted, and
 * {@link #messageFormatter()} defaults but can be overridden, following the same shape as
 * the SLF4J bridge module's (internal) {@code EventCreator} - a factory is naturally
 * scoped to one logger name and message formatter for its lifetime, the same way a real
 * SLF4J {@code Logger} is.
 * <p>
 * This class holds no mutable per-instance state - concrete subclasses are expected to be
 * immutable too - and is deliberately not {@code final}: extend it and override
 * individual methods to customize how events get constructed.
 *
 * @apiNote a builder that in turn builds/configures a factory was considered but is not
 * included (yet) - inheritance covers today's need (mainly tests) with less machinery.
 */
public abstract class LogEventFactory {

	/**
	 * For extending. Prefer {@link #of(String)} unless custom event construction is
	 * needed.
	 */
	protected LogEventFactory() {
	}

	/**
	 * Creates a factory bound to the given logger name, otherwise using default behavior
	 * for {@link #timestamp()}, {@link #threadName()}, {@link #threadId()} and
	 * {@link #messageFormatter()}.
	 * @param loggerName logger name every event created by the returned factory will
	 * have.
	 * @return factory.
	 */
	public static LogEventFactory of(String loggerName) {
		return new DefaultLogEventFactory(loggerName);
	}

	/**
	 * Name of the logger every event created by this factory will have.
	 * @return logger name.
	 */
	protected abstract String loggerName();

	/**
	 * Timestamp to use for the next event created by this factory. Default is
	 * {@link Instant#now()}.
	 * @return timestamp.
	 */
	protected Instant timestamp() {
		return Instant.now();
	}

	/**
	 * Thread name to use for the next event created by this factory. Default is
	 * {@link Thread#getName() Thread.currentThread().getName()}.
	 * @return thread name.
	 * @apiNote this maybe empty and often is if virtual threads are used.
	 */
	protected String threadName() {
		return Thread.currentThread().getName();
	}

	/**
	 * Thread id to use for the next event created by this factory. Default is
	 * {@link Thread#threadId() Thread.currentThread().threadId()}.
	 * @return thread id.
	 */
	protected long threadId() {
		return Thread.currentThread().threadId();
	}
	
	protected KeyValues defaultKeyValues() {
		return KeyValues.of();
	}

	/**
	 * Formatter to use for rendering a message when
	 * {@link LogEvent#formattedMessage(StringBuilder)} is called on events created by
	 * this factory that take arguments. Default is
	 * {@link LogMessageFormatter.StandardMessageFormatter#SLF4J}.
	 * @return message formatter.
	 * @apiNote a method rather than a parameter on the {@code event}/{@code eventArgs}
	 * methods below since this rarely changes per event - override it instead if a
	 * different formatter is needed.
	 */
	protected LogMessageFormatter messageFormatter() {
		return LogMessageFormatter.StandardMessageFormatter.SLF4J;
	}

	public LogEvent event(Level level, @Nullable String formattedMessage, @Nullable Throwable throwable) {
		return LogEvent.of(timestamp(), threadName(), threadId(), level, loggerName(), formattedMessage,
				defaultKeyValues(), null);
	}
	
	/**
	 * Creates a log event whose message is already formatted (no arguments). Corresponds
	 * to {@link LogEvent#of(Level, String, String, KeyValues, Throwable)}.
	 * @param level the logging level.
	 * @param formattedMessage the unformatted message.
	 * @param keyValues key values that come from MDC or an SLF4J Event Builder.
	 * @param throwable an exception if passed maybe <code>null</code>.
	 * @return event.
	 * @apiNote the message is already assumed to be formatted as no arguments are passed.
	 */
	public LogEvent event(Level level, @Nullable String formattedMessage, KeyValues keyValues,
			@Nullable Throwable throwable) {
		return LogEvent.of(timestamp(), threadName(), threadId(), level, loggerName(), formattedMessage,
				keyValues, throwable);
	}

	/**
	 * Creates a log event with a single message argument. Corresponds to
	 * {@link LogEvent#of(Level, String, String, KeyValues, LogMessageFormatter, Object)}.
	 * @param level the logging level.
	 * @param message the unformatted message.
	 * @param keyValues key values that come from MDC or an SLF4J Event Builder.
	 * @param arg1 argument that will be passed to {@link #messageFormatter()}.
	 * @return event.
	 */
	public LogEvent event(Level level, @Nullable String message, KeyValues keyValues, @Nullable Object arg1) {
		Instant timestamp = timestamp();
		String threadName = threadName();
		long threadId = threadId();
		String loggerName = loggerName();
		var messageFormatter = messageFormatter();
		return LogEvent.ofOneArg(timestamp, threadName, threadId, level, loggerName, message, keyValues, messageFormatter, arg1);
	}

	/**
	 * Creates a log event with two message arguments. Corresponds to
	 * {@link LogEvent#of(Level, String, String, KeyValues, LogMessageFormatter, Object, Object)}.
	 * @param level the logging level.
	 * @param message the unformatted message.
	 * @param keyValues key values that come from MDC or an SLF4J Event Builder.
	 * @param arg1 argument that will be passed to {@link #messageFormatter()}.
	 * @param arg2 argument that will be passed to {@link #messageFormatter()}.
	 * @return event.
	 */
	public LogEvent event(Level level, @Nullable String message, KeyValues keyValues, @Nullable Object arg1,
			@Nullable Object arg2) {
		Instant timestamp = timestamp();
		String threadName = threadName();
		long threadId = threadId();
		String loggerName = loggerName();
		var messageFormatter = messageFormatter();
		return LogEvent.ofTwoArgs(timestamp, threadName, threadId, level, loggerName, message, keyValues, messageFormatter, arg1, arg2);
	}

	/**
	 * Creates a log event with an array of message arguments. Corresponds to
	 * {@link LogEvent#ofArgs(Level, String, String, KeyValues, LogMessageFormatter, Object[])}.
	 * @param level the logging level.
	 * @param message the unformatted message.
	 * @param keyValues key values that come from MDC or an SLF4J Event Builder.
	 * @param args an array of arguments that will be passed to
	 * {@link #messageFormatter()}. The contents maybe null elements but the array itself
	 * should not be null.
	 * @return event.
	 */
	public LogEvent eventArgs(Level level, String message, KeyValues keyValues,
			@SuppressWarnings("exports") @Nullable Object @Nullable [] args) {
		return LogEvent.ofAll(timestamp(), threadName(), threadId(), level, loggerName(), message, keyValues, null,
				messageFormatter(), args);
	}

}

final class DefaultLogEventFactory extends LogEventFactory {

	private final String loggerName;

	DefaultLogEventFactory(String loggerName) {
		super();
		this.loggerName = loggerName;
	}

	@Override
	protected String loggerName() {
		return loggerName;
	}

}

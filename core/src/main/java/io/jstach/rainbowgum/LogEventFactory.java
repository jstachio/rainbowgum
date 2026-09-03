package io.jstach.rainbowgum;

import java.lang.System.Logger.Level;
import java.time.Instant;

import org.eclipse.jdt.annotation.Nullable;

/**
 * A dynamic version of {@link LogEvent}'s static "<code>of</code>" factory methods - same
 * method shapes (level, logger name, message, ...), but {@link #timestamp()},
 * {@link #threadName()} and {@link #threadId()} are resolved through overridable methods
 * instead of being pulled from {@link Instant#now()}/{@link Thread#currentThread()}
 * inline the way those static methods do. Those static methods are unaffected for now;
 * this is a first step toward eventually replacing them.
 * <p>
 * Not being able to override thread name/thread id/timestamp is a real limitation for
 * anything that needs deterministic events not tied to whichever real thread happens to
 * construct them - tests especially, but possibly other reasons later. A test-specific
 * subclass overriding those three methods once gets an entire family of
 * event-construction methods that are deterministic, rather than needing to thread fixed
 * values through every call site.
 * <p>
 * This class is immutable - it holds no per-instance state - and deliberately not
 * {@code final}: extend it and override individual methods (including
 * {@link #timestamp()}/{@link #threadName()}/{@link #threadId()} themselves) to customize
 * how events get constructed.
 *
 * @apiNote a builder that in turn builds/configures a factory was considered but is not
 * included (yet) - inheritance covers today's need (mainly tests) with less machinery.
 */
public class LogEventFactory {

	private static final LogEventFactory INSTANCE = new LogEventFactory();

	/**
	 * For extending. Prefer {@link #of()} unless custom event construction is needed.
	 */
	protected LogEventFactory() {
	}

	/**
	 * The default factory instance.
	 * @return factory.
	 */
	public static LogEventFactory of() {
		return INSTANCE;
	}

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

	/**
	 * Creates a log event whose message is already formatted (no arguments). Corresponds
	 * to {@link LogEvent#of(Level, String, String, KeyValues, Throwable)}.
	 * @param level the logging level.
	 * @param loggerName the name of the logger which is usually a class name.
	 * @param formattedMessage the unformatted message.
	 * @param keyValues key values that come from MDC or an SLF4J Event Builder.
	 * @param throwable an exception if passed maybe <code>null</code>.
	 * @return event.
	 * @apiNote the message is already assumed to be formatted as no arguments are passed.
	 */
	public LogEvent event(Level level, String loggerName, @Nullable String formattedMessage, KeyValues keyValues,
			@Nullable Throwable throwable) {
		return new DefaultLogEvent(timestamp(), threadName(), threadId(), level, loggerName, formattedMessage,
				keyValues, throwable);
	}

	/**
	 * Creates a log event with a single message argument. Corresponds to
	 * {@link LogEvent#of(Level, String, String, KeyValues, LogMessageFormatter, Object)}.
	 * @param level the logging level.
	 * @param loggerName the name of the logger which is usually a class name.
	 * @param message the unformatted message.
	 * @param keyValues key values that come from MDC or an SLF4J Event Builder.
	 * @param messageFormatter formatter to use for rendering a message when
	 * {@link LogEvent#formattedMessage(StringBuilder)} is called.
	 * @param arg1 argument that will be passed to messageFormatter.
	 * @return event.
	 */
	public LogEvent event(Level level, String loggerName, @Nullable String message, KeyValues keyValues,
			LogMessageFormatter messageFormatter, @Nullable Object arg1) {
		Instant timestamp = timestamp();
		String threadName = threadName();
		long threadId = threadId();
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
	 * Creates a log event with two message arguments. Corresponds to
	 * {@link LogEvent#of(Level, String, String, KeyValues, LogMessageFormatter, Object, Object)}.
	 * @param level the logging level.
	 * @param loggerName the name of the logger which is usually a class name.
	 * @param message the unformatted message.
	 * @param keyValues key values that come from MDC or an SLF4J Event Builder.
	 * @param messageFormatter formatter to use for rendering a message when
	 * {@link LogEvent#formattedMessage(StringBuilder)} is called.
	 * @param arg1 argument that will be passed to messageFormatter.
	 * @param arg2 argument that will be passed to messageFormatter.
	 * @return event.
	 */
	public LogEvent event(Level level, String loggerName, @Nullable String message, KeyValues keyValues,
			LogMessageFormatter messageFormatter, @Nullable Object arg1, @Nullable Object arg2) {
		Instant timestamp = timestamp();
		String threadName = threadName();
		long threadId = threadId();
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
	 * Creates a log event with an array of message arguments. Corresponds to
	 * {@link LogEvent#ofArgs(Level, String, String, KeyValues, LogMessageFormatter, Object[])}.
	 * @param level the logging level.
	 * @param loggerName the name of the logger which is usually a class name.
	 * @param message the unformatted message.
	 * @param keyValues key values that come from MDC or an SLF4J Event Builder.
	 * @param messageFormatter formatter to use for rendering a message when
	 * {@link LogEvent#formattedMessage(StringBuilder)} is called.
	 * @param args an array of arguments that will be passed to messageFormatter. The
	 * contents maybe null elements but the array itself should not be null.
	 * @return event.
	 */
	public LogEvent eventArgs(Level level, String loggerName, String message, KeyValues keyValues,
			LogMessageFormatter messageFormatter, @SuppressWarnings("exports") @Nullable Object @Nullable [] args) {
		return LogEvent.ofAll(timestamp(), threadName(), threadId(), level, loggerName, message, keyValues, null,
				messageFormatter, args);
	}

}

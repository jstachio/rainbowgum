package io.jstach.rainbowgum;

import java.lang.System.Logger.Level;
import java.time.Instant;

import org.eclipse.jdt.annotation.Nullable;

/**
 * A {@link LogEventFactory} for tests: fixes {@link #timestamp()}, {@link #threadName()}
 * and {@link #threadId()} to deterministic values.
 */
public final class TestLogEventFactory extends LogEventFactory {

	/**
	 * Fixed timestamp every event created by this factory has, unless overridden
	 * afterward via {@link LogEvent#freeze(Instant)}.
	 */
	public static final Instant FIXED_TIMESTAMP = Instant.EPOCH;

	/**
	 * Fixed thread name every event created by this factory has.
	 */
	public static final String FIXED_THREAD_NAME = "main";

	/**
	 * Fixed thread id every event created by this factory has.
	 */
	public static final long FIXED_THREAD_ID = 1L;

	/**
	 * Default message used by the no-arg/level-only {@code event(...)} overloads.
	 */
	public static final String DEFAULT_MESSAGE = "testMessage";

	private final String loggerName;

	private TestLogEventFactory(String loggerName) {
		this.loggerName = loggerName;
	}

	/**
	 * Creates a factory with logger name "test".
	 * @return factory.
	 */
	public static TestLogEventFactory of() {
		return new TestLogEventFactory("test");
	}

	/**
	 * Creates a factory bound to the given logger name.
	 * @param loggerName logger name.
	 * @return factory.
	 */
	public static TestLogEventFactory of(String loggerName) {
		return new TestLogEventFactory(loggerName);
	}

	@Override
	protected String loggerName() {
		return loggerName;
	}

	@Override
	protected Instant timestamp() {
		return FIXED_TIMESTAMP;
	}

	@Override
	protected String threadName() {
		return FIXED_THREAD_NAME;
	}

	@Override
	protected long threadId() {
		return FIXED_THREAD_ID;
	}

	/**
	 * Creates an event with level {@link Level#INFO} and {@link #DEFAULT_MESSAGE}.
	 * @return event.
	 */
	public LogEvent event() {
		return event(Level.INFO, DEFAULT_MESSAGE);
	}

	/**
	 * Creates an event with level {@link Level#INFO} and the given message.
	 * @param message message.
	 * @return event.
	 */
	public LogEvent event(String message) {
		return event(Level.INFO, message);
	}

	/**
	 * Creates an event with {@link #DEFAULT_MESSAGE} and the given level.
	 * @param level level.
	 * @return event.
	 */
	public LogEvent event(Level level) {
		return event(level, DEFAULT_MESSAGE);
	}

	/**
	 * Creates an event with the given level and message, default (empty)
	 * {@link KeyValues} and no throwable.
	 * @param level level.
	 * @param message message.
	 * @return event.
	 */
	public LogEvent event(Level level, String message) {
		return event(level, message, KeyValues.of(), (@Nullable Throwable) null);
	}

}

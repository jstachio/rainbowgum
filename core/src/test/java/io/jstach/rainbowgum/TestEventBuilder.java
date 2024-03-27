package io.jstach.rainbowgum;

import java.lang.System.Logger.Level;
import java.time.Instant;
import java.util.function.Consumer;

/**
 * Used to create events.
 */
public final class TestEventBuilder {

	private LogEventLogger logger = e -> {
		throw new UnsupportedOperationException();
	};

	private Level level = Level.INFO;

	private String loggerName = "test";

	/**
	 * Creates builder with level info and logger name test.
	 * @return this.
	 */
	public static TestEventBuilder of() {
		return new TestEventBuilder();
	}

	/**
	 * Set logger name.
	 * @param loggerName logger name
	 * @return this.
	 */
	public TestEventBuilder loggerName(String loggerName) {
		this.loggerName = loggerName;
		return this;
	}

	/**
	 * Set level.
	 * @param level level.
	 * @return this.
	 */
	public TestEventBuilder level(Level level) {
		this.level = level;
		return this;
	}

	/**
	 * Creates event builder.
	 * @return event builder.
	 */
	public LogEvent.Builder event() {
		return new LogEventBuilder(logger, level, loggerName).timestamp(Instant.EPOCH).message("testMessage");
	}

	/**
	 * Creates an event.
	 * @param consumer lambda to build event.
	 * @return event.
	 */
	public LogEvent build(Consumer<LogEvent.Builder> consumer) {
		var b = event();
		consumer.accept(b);
		var e = b.eventOrNull();
		if (e == null) {
			throw new IllegalStateException();
		}
		return e;
	}

	/**
	 * Creates an event.
	 * @return event.
	 */
	public LogEvent build() {
		var b = event();
		var e = b.eventOrNull();
		if (e == null) {
			throw new IllegalStateException();
		}
		return e;
	}

	/**
	 * Output to.
	 * @param logger output
	 * @return this.
	 */
	public TestEventBuilder to(LogEventLogger logger) {
		this.logger = logger;
		return this;
	}

}

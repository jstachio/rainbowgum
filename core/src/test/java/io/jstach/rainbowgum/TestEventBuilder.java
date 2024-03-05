package io.jstach.rainbowgum;

import java.lang.System.Logger.Level;

/**
 *
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
	 * @param loggerName logger name
	 * @return this.
	 */
	public TestEventBuilder loggerName(String loggerName) {
		this.loggerName = loggerName;
		return this;
	}

	/**
	 * @param level level.
	 * @return this.
	 */
	public TestEventBuilder level(Level level) {
		this.level = level;
		return this;
	}

	/**
	 * @return event builder.
	 */
	public LogEvent.Builder event() {
		return new LogEventBuilder(logger, level, loggerName);
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

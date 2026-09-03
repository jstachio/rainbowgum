package io.jstach.rainbowgum.pattern.format;

import java.time.Instant;

import io.jstach.rainbowgum.LogEventFactory;

/**
 * A {@link LogEventFactory} for tests: fixes {@link #timestamp()}, {@link #threadName()}
 * and {@link #threadId()} to deterministic values, since this module's pattern output
 * (e.g. {@code %thread}) can embed them in golden-string assertions.
 */
public final class TestLogEventFactory extends LogEventFactory {

	private final String loggerName;

	private TestLogEventFactory(String loggerName) {
		this.loggerName = loggerName;
	}

	public static TestLogEventFactory of(String loggerName) {
		return new TestLogEventFactory(loggerName);
	}

	@Override
	protected String loggerName() {
		return loggerName;
	}

	@Override
	protected Instant timestamp() {
		return Instant.EPOCH;
	}

	@Override
	protected String threadName() {
		return "main";
	}

	@Override
	protected long threadId() {
		return 1L;
	}

}

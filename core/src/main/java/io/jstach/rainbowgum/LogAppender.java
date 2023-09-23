package io.jstach.rainbowgum;

import static java.util.Objects.requireNonNull;

import java.util.List;

import io.jstach.rainbowgum.jansi.JansiLogFormatter;

/**
 * Appenders are guaranteed to be written synchronously much like an actor in actor
 * concurrency.
 */
public interface LogAppender extends AutoCloseable {

	/*
	 * danger
	 */
	default void append(LogEvent[] event, int count) {
		for (int i = 0; i < count; i++) {
			append(event[i]);
		}
	}

	public void append(LogEvent event);

	public static Builder builder() {
		return new Builder();
	}

	public static LogAppender of(List<? extends LogAppender> appenders) {
		return new CompositeLogAppender(appenders.toArray(new LogAppender[] {}));
	}

	public static class Builder {

		protected LogOutput output = LogOutput.ofStandardOut();

		protected LogFormatter formatter = JansiLogFormatter.builder().build();

		private Builder() {
		}

		public Builder output(LogOutput output) {
			this.output = output;
			return this;
		}

		public Builder formatter(LogFormatter formatter) {
			this.formatter = formatter;
			return this;
		}

		public Builder formatter(LogFormatter.EventFormatter formatter) {
			this.formatter = formatter;
			return this;
		}

		public LogAppender build() {
			return new DefaultLogAppender(requireNonNull(output), requireNonNull(formatter));
		}

	}

	@Override
	default void close() {
	}

}

record CompositeLogAppender(LogAppender[] appenders) implements LogAppender {

	public void append(LogEvent[] event, int count) {
		for (var appender : appenders) {
			appender.append(event, count);
		}
	}

	@Override
	public void append(LogEvent event) {
		for (var appender : appenders) {
			appender.append(event);
		}
	}

	@Override
	public void close() {
		for (var appender : appenders) {
			appender.close();
		}
	}

}

record DefaultLogAppender(LogOutput output, LogFormatter formatter) implements LogAppender {
	@Override
	public void append(LogEvent event) {
		StringBuilder sb = new StringBuilder();
		formatter.format(sb, event);
		output.write(event, sb.toString());
		output.flush();

	}

	@Override
	public void close() {
		output.close();
	}
}

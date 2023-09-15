package io.jstach.rainbowgum;

import static java.util.Objects.requireNonNull;

import io.jstach.rainbowgum.jansi.JansiLogFormatter;

public interface LogAppender extends AutoCloseable {

	public void append(LogEvent event);

	public static Builder builder() {
		return new Builder();
	}

	public static class Builder {

		protected LogOutput output = LogEncoder.of(System.out);

		protected LogFormatter.EventFormatter formatter = JansiLogFormatter.builder().build();

		private Builder() {
		}

		public Builder output(LogOutput output) {
			this.output = output;
			return this;
		}

		public Builder formatter(LogFormatter.EventFormatter formatter) {
			this.formatter = formatter;
			return this;
		}

		LogAppender build() {
			return new DefaultLogAppender(requireNonNull(output), requireNonNull(formatter));
		}

	}

	@Override
	default void close() {
	}

}

record DefaultLogAppender(LogOutput output, LogFormatter.EventFormatter formatter) implements LogAppender {
	@Override
	public void append(LogEvent event) {
		formatter.format(output, event);

	}
}

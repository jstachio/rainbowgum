package io.jstach.rainbowgum;

import static java.util.Objects.requireNonNull;

import java.util.List;

import org.eclipse.jdt.annotation.Nullable;

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

		protected @Nullable LogFormatter formatter;

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
			var f = this.formatter;
			if (f == null) {
				f = Defaults.CONSOLE.defaultFormatter.get();
			}
			return new DefaultLogAppender(requireNonNull(output), requireNonNull(f));
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

final class DefaultLogAppender implements LogAppender {

	protected final LogOutput output;

	protected final LogFormatter formatter;

	protected final StringBuilder sb = new StringBuilder();

	public DefaultLogAppender(LogOutput output, LogFormatter formatter) {
		super();
		this.output = output;
		this.formatter = formatter;
	}

	@Override
	public void append(LogEvent event) {
		/*
		 * TODO trim size of StringBuilder if it gets too large
		 */
		sb.setLength(0);
		formatter.format(sb, event);
		output.write(event, sb.toString());
		output.flush();

	}

	@Override
	public void close() {
		output.close();
	}

}

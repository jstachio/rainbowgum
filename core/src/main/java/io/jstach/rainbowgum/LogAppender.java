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
		if (appenders.isEmpty()) {
			throw new IllegalArgumentException("A single appender is required");
		}
		if (appenders.size() == 1) {
			return appenders.get(0);
		}
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
				f = Defaults.formatterForOutputType(output.type());
			}
			return new DefaultLogAppender(requireNonNull(output), requireNonNull(f));
		}

	}

	@Override
	public void close();

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

	protected final StringBuilder buffer = new StringBuilder();

	protected final int maxStringBuilderSize = Defaults.maxStringBuilderSize.getAsInt();

	public DefaultLogAppender(LogOutput output, LogFormatter formatter) {
		super();
		this.output = output;
		this.formatter = formatter;
	}

	@Override
	public void append(LogEvent event) {
		boolean shrink = buffer.length() > maxStringBuilderSize;
		buffer.setLength(0);
		formatter.format(buffer, event);
		output.write(event, buffer.toString());
		output.flush();
		if (shrink && buffer.length() < maxStringBuilderSize) {
			buffer.trimToSize();
		}

	}

	@Override
	public void close() {
		output.close();
	}

}

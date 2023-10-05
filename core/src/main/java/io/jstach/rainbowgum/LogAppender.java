package io.jstach.rainbowgum;

import static java.util.Objects.requireNonNull;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.locks.ReentrantLock;

import org.eclipse.jdt.annotation.Nullable;

import io.jstach.rainbowgum.LogAppender.LockAwareLogAppender;
import io.jstach.rainbowgum.LogAppender.LockingLogAppender;

/**
 * Appenders are guaranteed to be written synchronously much like an actor in actor
 * concurrency.
 */
public interface LogAppender extends AutoCloseable, LogEventConsumer {

	/*
	 * danger
	 */
	default void append(LogEvent[] events, int count) {
		for (int i = 0; i < count; i++) {
			append(events[i]);
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

		public Provider<LogAppender> build() {
			var _output = output;
			var _formatter = formatter;
			return config -> {
				var f = _formatter;
				var o = _output;
				if (_formatter == null) {
					f = Defaults.formatterForOutputType(o.type());
				}
				return Defaults.logAppender.provide(config, requireNonNull(o), requireNonNull(f));
			};
		}

	}

	@Override
	public void close();

	public interface LockAwareLogAppender extends LogAppender {

		public LogEventConsumer runLocked(LogEvent event);

	}

	public interface LockingLogAppender extends LogAppender {

		public static LockingLogAppender of(LogAppender appender) {
			if (appender instanceof LockingLogAppender lo) {
				return lo;
			}
			return new DefaultLockingLogAppender(appender);
		}

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

class DefaultLockingLogAppender implements LockingLogAppender {

	private final LogAppender appender;

	private final ReentrantLock lock = new ReentrantLock();

	public DefaultLockingLogAppender(LogAppender appender) {
		this.appender = appender;
	}

	@Override
	public void append(LogEvent[] events, int count) {
		lock.lock();
		try {
			for (int i = 0; i < count; i++) {
				append(events[i]);
			}
		}
		finally {
			lock.unlock();
		}
	}

	@Override
	public void append(LogEvent event) {
		LogEventConsumer a = appender;
		lock.lock();
		try {
			a.append(event);
		}
		finally {
			lock.unlock();
		}

	}

	@Override
	public void close() {
		appender.close();
	}

}

abstract class AbstractLogAppender implements LogAppender {

	protected final LogOutput output;

	protected final LogFormatter formatter;

	public AbstractLogAppender(LogOutput output, LogFormatter formatter) {
		super();
		this.output = output;
		this.formatter = formatter;
	}

	@Override
	public void close() {
		output.close();
	}

}

class VirtualThreadLogAppender extends AbstractLogAppender implements LockingLogAppender {

	private final ReentrantLock lock = new ReentrantLock();

	public VirtualThreadLogAppender(LogOutput output, LogFormatter formatter) {
		super(output, formatter);
	}

	@Override
	public void append(LogEvent[] events, int count) {
		lock.lock();
		try {
			StringBuilder sb = new StringBuilder();
			for (int i = 0; i < count; i++) {
				append(events[i], sb);
			}
		}
		finally {
			lock.unlock();
		}
	}

	private void append(LogEvent event, StringBuilder sb) {
		formatter.format(sb, event);
		String result = sb.toString();
		lock.lock();
		try {
			output.write(event, result);
			output.flush();
		}
		finally {
			lock.unlock();
		}
	}

	@Override
	public void append(LogEvent event) {
		StringBuilder sb = new StringBuilder();
		append(event, sb);
	}

	@Override
	public void close() {
		lock.lock();
		try {
			super.close();
		}
		finally {
			lock.unlock();
		}
	}

}

class DefaultLockAwareLogAppender implements LockAwareLogAppender {

	protected final LogOutput output;

	protected final LogFormatter formatter;

	protected final StringBuilder buffer = new StringBuilder();

	protected final int maxStringBuilderSize = Defaults.maxStringBuilderSize.getAsInt();

	public DefaultLockAwareLogAppender(LogOutput output, LogFormatter formatter) {
		super();
		this.output = output;
		this.formatter = formatter;
	}

	@Override
	public LogEventConsumer runLocked(LogEvent event) {
		byte[] data = encode(event, new StringBuilder());
		return (e) -> {
			output.write(e, data);
		};
	}

	public void write(LogEvent event, byte[] data) {
		output.write(event, data);
		output.flush();
	}

	@Override
	public void append(LogEvent event) {
		boolean shrink = buffer.length() > maxStringBuilderSize;
		buffer.setLength(0);
		var data = encode(event, buffer);
		write(event, data);
		if (shrink && buffer.length() < maxStringBuilderSize) {
			buffer.trimToSize();
		}

	}

	protected byte[] encode(LogEvent event, StringBuilder buffer) {
		formatter.format(buffer, event);
		return buffer.toString().getBytes(StandardCharsets.UTF_8);
	}

	@Override
	public void close() {
		output.close();
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

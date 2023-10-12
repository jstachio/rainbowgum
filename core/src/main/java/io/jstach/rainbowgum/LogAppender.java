package io.jstach.rainbowgum;

import static java.util.Objects.requireNonNull;

import java.util.List;
import java.util.concurrent.locks.ReentrantLock;

import org.eclipse.jdt.annotation.Nullable;

import io.jstach.rainbowgum.LogAppender.AbstractLogAppender;
import io.jstach.rainbowgum.LogAppender.ThreadSafeLogAppender;
import io.jstach.rainbowgum.LogEncoder.Buffer;
import io.jstach.rainbowgum.LogEncoder.BufferProvider;

/**
 * Appenders are guaranteed to be written synchronously much like an actor in actor
 * concurrency.
 */
public interface LogAppender extends AutoCloseable, LogEventConsumer {

	/**
	 * Batch of events. <strong>DO NOT MODIFY THE ARRAY<strong>. Do not use the
	 * <code>length</code> of the passed in array but instead use <code>count</code>
	 * parameter.
	 * @param events an array guaranteed to be smaller than count.
	 * @param count the number of items.
	 */
	default void append(LogEvent[] events, int count) {
		for (int i = 0; i < count; i++) {
			append(events[i]);
		}
	}

	public void append(LogEvent event);

	public interface AppenderProvider {
		LogAppender provide(LogConfig config);
		public static Builder builder() {
			return LogAppender.builder();
		}
	}
	
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

		public AppenderProvider build() {
			var _output = output;
			var _formatter = formatter;
			var f = _formatter;
			var o = _output;
			if (_formatter == null) {
				f = Defaults.formatterForOutputType(o.type());
			}
			return Defaults.logAppender.provide(requireNonNull(o), requireNonNull(f));
		}

	}

	@Override
	public void close();

	public interface ThreadSafeLogAppender extends LogAppender {

		public static ThreadSafeLogAppender of(LogAppender appender) {
			if (appender instanceof ThreadSafeLogAppender lo) {
				return lo;
			}
			return Defaults.threadSafeAppender.apply(appender);
		}

	}

	abstract class AbstractLogAppender implements LogAppender {

		protected final LogOutput output;

		protected final LogEncoder encoder;

		protected final BufferProvider bufferProvider;

		
		public AbstractLogAppender(LogOutput output, LogEncoder encoder, BufferProvider bufferProvider) {
			super();
			this.output = output;
			this.encoder = encoder;
			this.bufferProvider = bufferProvider;
		}
		
		

		public AbstractLogAppender(
				LogOutput output,
				LogEncoder encoder) {
			this(output, encoder, BufferProvider.of());
		}

		@Override
		public final void append(LogEvent event) {
			try (var buffer = bufferProvider.provideBuffer()) {
				append(event, buffer);
			}
		}

		@Override
		public final void append(LogEvent[] events, int count) {
			try (var buffer = bufferProvider.provideBuffer()) {
				append(events, count, buffer);
			}
		}

		protected void append(LogEvent[] events, int count, Buffer buffer) {
			for (int i = 0; i < count; i++) {
				append(events[i], buffer);
				buffer.clear();
			}
		}

		protected abstract void append(LogEvent event, Buffer buffer);

		@Override
		public void close() {
			output.close();
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

class LockingLogAppender implements ThreadSafeLogAppender {

	private final LogAppender appender;

	private final ReentrantLock lock = new ReentrantLock();

	public LockingLogAppender(LogAppender appender) {
		this.appender = appender;
	}

	@Override
	public void append(LogEvent[] events, int count) {
		lock.lock();
		try {
			appender.append(events, count);
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

/*
 * The idea here is to have the virtual thread do the formatting outside of the lock
 */
final class DefaultLogAppender extends AbstractLogAppender implements ThreadSafeLogAppender {

	private final ReentrantLock lock = new ReentrantLock();

	public DefaultLogAppender(LogOutput output, LogEncoder encoder) {
		super(output, encoder);
	}

	@Override
	protected void append(LogEvent[] events, int count, Buffer buffer) {
		lock.lock();
		try {
			for (int i = 0; i < count; i++) {
				append(events[i], buffer);
				buffer.clear();
			}
		}
		finally {
			lock.unlock();
		}

	}

	protected void append(LogEvent event, Buffer buffer) {
		encoder.encode(event, buffer);
		lock.lock();
		try {
			buffer.drain(output, event);
			output.flush();
		}
		finally {
			lock.unlock();
		}
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

final class SimpleLogAppender implements LogAppender {

	protected final LogFormatter formatter;

	protected final StringBuilder buffer = new StringBuilder();

	protected final int maxStringBuilderSize = Defaults.maxStringBuilderSize.getAsInt();

	protected final LogOutput output;

	public SimpleLogAppender(LogOutput output, LogFormatter formatter) {
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

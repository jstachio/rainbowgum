package io.jstach.rainbowgum;

import java.util.List;
import java.util.concurrent.locks.ReentrantLock;

import org.eclipse.jdt.annotation.Nullable;

import io.jstach.rainbowgum.LogAppender.AbstractLogAppender;
import io.jstach.rainbowgum.LogAppender.ThreadSafeLogAppender;
import io.jstach.rainbowgum.LogEncoder.Buffer;

/**
 * Appenders are guaranteed to be written synchronously much like an actor in actor
 * concurrency.
 *
 * The only exception is if an Appender implements {@link ThreadSafeLogAppender}.
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

	/**
	 * A factor of appenders.
	 */
	public interface AppenderProvider {

		/**
		 * Creates an appender from config.
		 * @param config config.
		 * @return appender.
		 */
		LogAppender provide(LogConfig config);

		/**
		 * Creates a builder to create an appender provider.
		 * @return builder.
		 */
		public static Builder builder() {
			return LogAppender.builder();
		}

	}

	/**
	 * Creates a builder.
	 * @return builder.
	 */
	public static Builder builder() {
		return new Builder();
	}

	/**
	 * Creates a composite log appender from many. The appenders will be appended
	 * synchronously.
	 * @param appenders appenders.
	 * @return appender.
	 */
	public static LogAppender of(List<? extends LogAppender> appenders) {
		if (appenders.isEmpty()) {
			throw new IllegalArgumentException("A single appender is required");
		}
		if (appenders.size() == 1) {
			return appenders.get(0);
		}
		return new CompositeLogAppender(appenders.toArray(new LogAppender[] {}));
	}

	/**
	 * Builder for creating standard appenders.
	 */
	public static class Builder {

		protected LogOutput output = LogOutput.ofStandardOut();

		protected @Nullable LogEncoder encoder;

		private Builder() {
		}

		/**
		 * Sets output
		 * @param output
		 * @return builder.
		 */
		public Builder output(LogOutput output) {
			this.output = output;
			return this;
		}

		/**
		 * Sets encoder as formatter.
		 * @param formatter
		 * @return builder.
		 */
		public Builder formatter(LogFormatter formatter) {
			this.encoder = LogEncoder.of(formatter);
			return this;
		}

		/**
		 * Sets encoder as formatter.
		 * @param formatter
		 * @return builder.
		 */
		public Builder formatter(LogFormatter.EventFormatter formatter) {
			this.encoder = LogEncoder.of(formatter);
			return this;
		}

		/**
		 * Sets encoder.
		 * @param encoder
		 * @return builder.
		 */
		public Builder encoder(LogEncoder encoder) {
			this.encoder = encoder;
			return this;
		}

		/**
		 * Builds.
		 * @return an appender factory.
		 */
		public AppenderProvider build() {
			var output = this.output;
			var encoder = this.encoder;
			return config -> {
				return config.defaults().logAppender(output, encoder);
			};
		}

	}

	@Override
	public void close();

	/**
	 * An appender that can be used by a {@link LogPublisher.SyncLogPublisher}.
	 */
	public interface ThreadSafeLogAppender extends LogAppender {

		/**
		 * Make an appender thread safe if is not already thread safe.
		 * @param appender appender.
		 * @return new thread safe appender if is not one or the passed in appender if it
		 * thread safe.
		 */
		public static ThreadSafeLogAppender of(LogAppender appender) {
			if (appender instanceof ThreadSafeLogAppender lo) {
				return lo;
			}
			return Defaults.threadSafeAppender.apply(appender);
		}

	}

	/**
	 * An abstract appender to help create custom appenders.
	 */
	abstract class AbstractLogAppender implements LogAppender {

		protected final LogOutput output;

		protected final LogEncoder encoder;

		public AbstractLogAppender(LogOutput output, LogEncoder encoder) {
			super();
			this.output = output;
			this.encoder = encoder;
		}

		@Override
		public final void append(LogEvent event) {
			try (var buffer = encoder.buffer()) {
				append(event, buffer);
			}
		}

		@Override
		public void append(LogEvent[] events, int count) {
			try (var buffer = encoder.buffer()) {
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
class DefaultLogAppender extends AbstractLogAppender implements ThreadSafeLogAppender {

	protected final ReentrantLock lock = new ReentrantLock();

	public DefaultLogAppender(LogOutput output, LogEncoder encoder) {
		super(output, encoder);
	}

	@Override
	protected void append(LogEvent[] events, int count, Buffer buffer) {
		lock.lock();
		try {
			for (int i = 0; i < count; i++) {
				append(events[i], buffer);
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

class BufferLogAppender extends AbstractLogAppender {

	public BufferLogAppender(LogOutput output, LogEncoder encoder) {
		super(output, CachedEncoder.of(encoder));
	}

	@Override
	protected void append(LogEvent event, Buffer buffer) {
		buffer.drain(output, event);
	}

}

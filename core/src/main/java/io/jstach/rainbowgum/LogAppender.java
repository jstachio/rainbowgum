package io.jstach.rainbowgum;

import java.util.List;
import java.util.Objects;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Function;

import org.eclipse.jdt.annotation.NonNull;
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
public sealed interface LogAppender extends LogLifecycle, LogEventConsumer, LogConfig.Provider<LogAppender> {

	/**
	 * Default Console appender name.
	 */
	static final String CONSOLE_APPENDER_NAME = "console";

	/**
	 * Default output file appender name.
	 */
	static final String FILE_APPENDER_NAME = "file";

	/**
	 * Output appender property.
	 */
	static final String APPENDER_OUTPUT_PROPERTY = LogProperties.APPENDER_OUTPUT_PROPERTY;

	/**
	 * Encoder appender property.
	 */
	static final String APPENDER_ENCODER_PROPERTY = LogProperties.APPENDER_ENCODER_PROPERTY;

	/**
	 * Batch of events. <strong>DO NOT MODIFY THE ARRAY</strong>. Do not use the
	 * <code>length</code> of the passed in array but instead use <code>count</code>
	 * parameter.
	 * @param events an array guaranteed to be smaller than count.
	 * @param count the number of items.
	 */
	public void append(LogEvent[] events, int count);

	@Override
	public void append(LogEvent event);

	@Override
	default LogAppender provide(String name, LogConfig config) {
		return this;
	}

	/**
	 * Creates a builder.
	 * @param name appender name.
	 * @return builder.
	 */
	public static Builder builder(String name) {
		return new Builder(name);
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
			return Objects.requireNonNull(appenders.get(0));
		}
		@SuppressWarnings("null") // TODO Eclipse issue here
		LogAppender @NonNull [] array = appenders.toArray(new LogAppender[] {});
		return new CompositeLogAppender(array);
	}

	/**
	 * Builder for creating standard appenders.
	 * <p>
	 * If the output is not set standard out will be used. If the encoder is not set a
	 * default encoder will be resolved from the output.
	 */
	public static final class Builder {

		private LogConfig.@Nullable Provider<? extends LogOutput> output = null;

		private LogConfig.@Nullable Provider<? extends LogEncoder> encoder = null;

		private final String name;

		private Builder(String name) {
			this.name = name;
		}

		/**
		 * Name of the appender.
		 * @return name.
		 */
		public String name() {
			return this.name;
		}

		/**
		 * Sets output.
		 * @param output output.
		 * @return builder.
		 */
		public Builder output(LogConfig.Provider<? extends LogOutput> output) {
			this.output = output;
			return this;
		}

		/**
		 * Sets output.
		 * @param output output.
		 * @return builder.
		 */
		public Builder output(LogOutput output) {
			this.output = LogConfig.Provider.of(output);
			return this;
		}

		/**
		 * Sets formatter as encoder.
		 * @param formatter formatter to be converted to encoder.
		 * @return builder.
		 * @see LogEncoder#of(LogFormatter)
		 */
		public Builder formatter(LogFormatter formatter) {
			this.encoder = LogConfig.Provider.of(LogEncoder.of(formatter));
			return this;
		}

		/**
		 * Sets formatter as encoder.
		 * @param formatter formatter to be converted to encoder.
		 * @return builder.
		 * @see LogEncoder#of(LogFormatter)
		 */
		public Builder formatter(LogFormatter.EventFormatter formatter) {
			this.encoder = LogConfig.Provider.of(LogEncoder.of(formatter));
			return this;
		}

		/**
		 * Sets encoder.
		 * @param encoder encoder not <code>null</code>.
		 * @return builder.
		 */
		public Builder encoder(LogConfig.Provider<? extends LogEncoder> encoder) {
			this.encoder = encoder;
			return this;
		}

		/**
		 * Sets encoder.
		 * @param encoder encoder not <code>null</code>.
		 * @return builder.
		 */
		public Builder encoder(LogEncoder encoder) {
			this.encoder = LogConfig.Provider.of(encoder);
			return this;
		}

		/**
		 * Builds.
		 * @return an appender factory.
		 */
		public LogConfig.Provider<LogAppender> build() {
			/*
			 * We need to capture parameters since appender creation needs to be lazy.
			 */
			var _name = name;
			var _output = output;
			var _encoder = encoder;
			/*
			 * TODO should we use the parent name for resolution?
			 */
			return (n, config) -> {
				AppenderConfig a = new AppenderConfig(_name, LogConfig.Provider.provideOrNull(_name, _output, config),
						LogConfig.Provider.provideOrNull(_name, _encoder, config));
				return DefaultAppenderRegistry.appender(a, config);
			};
		}

	}

	@Override
	public void close();

	/**
	 * An appender that can be used by a {@link LogPublisher.SyncLogPublisher}.
	 */
	public sealed interface ThreadSafeLogAppender extends LogAppender {

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
			return DefaultLogAppender.threadSafeAppender.apply(appender);
		}

	}

	/**
	 * An abstract appender to help create custom appenders.
	 */
	sealed abstract class AbstractLogAppender implements LogAppender {

		/**
		 * output
		 */
		protected final LogOutput output;

		/**
		 * encoder
		 */
		protected final LogEncoder encoder;

		/**
		 * Creates an appender from an output and encoder.
		 * @param output set the output field and will be started and closed with the
		 * appender.
		 * @param encoder set the encoder field.
		 */
		protected AbstractLogAppender(LogOutput output, LogEncoder encoder) {
			super();
			this.output = output;
			this.encoder = encoder;
		}

		@Override
		public void start(LogConfig config) {
			output.start(config);
		}

		@Override
		public void close() {
			output.close();
		}

	}

}

record CompositeLogAppender(LogAppender[] appenders) implements LogAppender {

	@Override
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

	@Override
	public void start(LogConfig config) {
		for (var appender : appenders) {
			appender.start(config);
		}
	}

}

final class LockingLogAppender implements ThreadSafeLogAppender {

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
		lock.lock();
		try {
			appender.append(event);
		}
		finally {
			lock.unlock();
		}

	}

	@Override
	public void start(LogConfig config) {
		appender.start(config);
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

	static Function<LogAppender, ThreadSafeLogAppender> threadSafeAppender = (appender) -> {
		return new LockingLogAppender(appender);
	};

	private final ReentrantLock lock = new ReentrantLock();

	public DefaultLogAppender(LogOutput output, LogEncoder encoder) {
		super(output, encoder);
	}

	@Override
	public final void append(LogEvent event) {
		try (var buffer = encoder.buffer(output.bufferHints())) {
			encoder.encode(event, buffer);
			lock.lock();
			try {
				output.write(event, buffer);
			}
			finally {
				lock.unlock();
			}
		}
	}

	@Override
	public void append(LogEvent[] events, int count) {
		lock.lock();
		try {
			output.write(events, count, encoder);
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

final class BufferLogAppender extends AbstractLogAppender {

	private final Buffer buffer;

	public BufferLogAppender(LogOutput output, LogEncoder encoder) {
		super(output, encoder);
		this.buffer = encoder.buffer(output.bufferHints());
	}

	@Override
	public void append(LogEvent[] events, int count) {
		buffer.clear();
		output.write(events, count, encoder, buffer);
		output.flush();
	}

	@Override
	public void append(LogEvent event) {
		buffer.clear();
		output.write(event, buffer);
		output.flush();
	}

}

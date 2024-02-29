package io.jstach.rainbowgum.appender;

import java.util.concurrent.locks.ReentrantLock;

import io.jstach.rainbowgum.LogAppender;
import io.jstach.rainbowgum.LogAppender.AbstractLogAppender;
import io.jstach.rainbowgum.LogAppender.ThreadSafeLogAppender;
import io.jstach.rainbowgum.LogConfig;
import io.jstach.rainbowgum.LogEncoder;
import io.jstach.rainbowgum.LogEvent;
import io.jstach.rainbowgum.LogOutput;

/**
 * An appender that uses <code>synchronized</code> instead of {@linkplain ReentrantLock
 * locks}. <strong>DO NOT USE THIS APPENDER IN A VIRTUAL THREAD ENVIROMENT</strong> as
 * virtual threads will (as of JDK 21) pin the carrier thread when in a synchronized
 * block. This appender is currently mainly for benchmarking/testing purposes.
 */
public sealed interface SynchronizedLogAppender extends ThreadSafeLogAppender {

	/**
	 * Decorates an appender with a synchronized appender.
	 * @param appender appender to be decorated.
	 * @return appender.
	 */
	public static SynchronizedLogAppender of(LogAppender appender) {
		if (appender instanceof SynchronizedLogAppender s) {
			return s;
		}
		return new SynchronizedDecorator(appender);
	}

	/**
	 * Creates a plain appender that is synchronized.
	 * @param output output.
	 * @param encoder encoder.
	 * @return appender.
	 */
	public static SynchronizedLogAppender of(LogOutput output, LogEncoder encoder) {
		return new DefaultSynchronizedLogAppender(output, encoder);
	}

}

final class SynchronizedDecorator implements SynchronizedLogAppender {

	private final LogAppender appender;

	public SynchronizedDecorator(LogAppender appender) {
		this.appender = appender;
	}

	@Override
	public synchronized void append(LogEvent[] events, int count) {
		appender.append(events, count);
	}

	@Override
	public synchronized void append(LogEvent event) {
		appender.append(event);
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

final class DefaultSynchronizedLogAppender extends AbstractLogAppender implements SynchronizedLogAppender {

	public DefaultSynchronizedLogAppender(LogOutput output, LogEncoder encoder) {
		super(output, encoder);
	}

	@Override
	public final void append(LogEvent event) {
		try (var buffer = encoder.buffer()) {
			encoder.encode(event, buffer);
			synchronized (this) {
				output.write(event, buffer);
			}
		}
	}

	@Override
	public void append(LogEvent[] events, int count) {
		synchronized (this) {
			output.write(events, count, encoder);
			output.flush();
		}
	}

	@Override
	public void close() {
		synchronized (this) {
			super.close();
		}
	}

}
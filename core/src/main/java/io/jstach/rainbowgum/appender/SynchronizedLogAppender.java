package io.jstach.rainbowgum.appender;

import io.jstach.rainbowgum.LogAppender;
import io.jstach.rainbowgum.LogAppender.AbstractLogAppender;
import io.jstach.rainbowgum.LogAppender.ThreadSafeLogAppender;
import io.jstach.rainbowgum.LogEncoder;
import io.jstach.rainbowgum.LogEncoder.Buffer;
import io.jstach.rainbowgum.LogEvent;
import io.jstach.rainbowgum.LogOutput;

public sealed interface SynchronizedLogAppender extends ThreadSafeLogAppender {

	public static SynchronizedLogAppender of(LogAppender appender) {
		return new SynchronizedDecorator(appender);
	}

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
	public void close() {
		appender.close();
	}

}

final class DefaultSynchronizedLogAppender extends AbstractLogAppender implements SynchronizedLogAppender {

	public DefaultSynchronizedLogAppender(LogOutput output, LogEncoder encoder) {
		super(output, encoder);
	}

	@Override
	protected void append(LogEvent[] events, int count, Buffer buffer) {
		synchronized (this) {
			super.append(events, count, buffer);
		}

	}

	@Override
	protected void append(LogEvent event, Buffer buffer) {
		encoder.encode(event, buffer);
		synchronized (this) {
			buffer.drain(output, event);
		}
	}

	@Override
	public void close() {
		synchronized (this) {
			super.close();
		}
	}

}
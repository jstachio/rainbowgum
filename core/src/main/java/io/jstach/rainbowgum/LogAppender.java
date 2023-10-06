package io.jstach.rainbowgum;

import static java.util.Objects.requireNonNull;

import java.util.List;
import java.util.concurrent.locks.ReentrantLock;

import org.eclipse.jdt.annotation.Nullable;

import io.jstach.rainbowgum.LogAppender.AbstractLogAppender;
import io.jstach.rainbowgum.LogAppender.LockingLogAppender;
import io.jstach.rainbowgum.LogOutput.ThreadSafeLogOutput;

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

	public interface LockingLogAppender extends LogAppender {

		public static LockingLogAppender of(LogAppender appender) {
			if (appender instanceof LockingLogAppender lo) {
				return lo;
			}
			return Defaults.lockingAppender.apply(appender);
		}

	}

	abstract class AbstractLogAppender implements LogAppender {

		protected final LogOutput output;

		public AbstractLogAppender(LogOutput output) {
			super();
			this.output = output;
		}

		@Override
		public void close() {
			output.close();
		}

	}
	
	class FormattingLogAppender extends AbstractLogAppender implements LockingLogAppender {

		protected LogFormatter formatter;
		
		public FormattingLogAppender(
				ThreadSafeLogOutput output, LogFormatter formatter) {
			super(
					output);
			this.formatter = formatter;
		}
		
		@Override
		public final void append(
				LogEvent event) {
			var buffer = createBuffer();
			int size = buffer.length();
			append(event, createBuffer());
			releaseBuffer(buffer, size);
		}
		
		@Override
		public final void append(
				LogEvent[] events,
				int count) {
			var buffer = createBuffer();
			int size = buffer.length();
			append(events, count, buffer);
			releaseBuffer(buffer, size);
		}
		
		protected StringBuilder createBuffer() {
			return new StringBuilder();
		}
		
		protected void releaseBuffer(StringBuilder buffer, int originalSize) {
		}
		
		public String format(LogEvent event, StringBuilder sb) {
			this.formatter.format(sb, event);
			return sb.toString();
		}

		protected void appendLocked(
				LogEvent event, String formatted) {
			output.write(event, formatted);
		}
		
		protected void append(
				LogEvent[] events,
				int count,
				StringBuilder sb) {
			for (int i = 0; i < count; i++) {
				sb.setLength(0);
				append(events[i], sb);
			}
		}
		
		protected void append(
				LogEvent event, StringBuilder sb) {
			String formatted = format(event, sb);
			appendLocked(event, formatted);
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

class AbstractLockingLogAppender implements LockingLogAppender {

	
	@Override
	public void append(
			LogEvent[] events,
			int count) {
		// TODO Auto-generated method stub
		LockingLogAppender.super.append(events, count);
	}
	
	@Override
	public void append(
			LogEvent event) {
		// TODO Auto-generated method stub
		
	}

	@Override
	public void close() {
		// TODO Auto-generated method stub
		
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

class SynchronizedLockingLogAppender implements LockingLogAppender {

	private final LogAppender appender;

	public SynchronizedLockingLogAppender(LogAppender appender) {
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

/*
 * The idea here is to have the virtual thread do the formatting outside of the lock
 */
class VirtualThreadLogAppender extends AbstractLogAppender implements LockingLogAppender {

	private final ReentrantLock lock = new ReentrantLock();
	protected final LogFormatter formatter;
	public VirtualThreadLogAppender(LogOutput output, LogFormatter formatter) {
		super(output);
		this.formatter = formatter;
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

final class DefaultLogAppender extends AbstractLogAppender {

	protected final LogFormatter formatter;

	protected final StringBuilder buffer = new StringBuilder();

	protected final int maxStringBuilderSize = Defaults.maxStringBuilderSize.getAsInt();

	public DefaultLogAppender(LogOutput output, LogFormatter formatter) {
		super(output);
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

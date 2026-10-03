package io.jstach.rainbowgum;

import java.lang.System.Logger.Level;
import java.time.Duration;
import java.util.Arrays;
import java.util.Objects;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;

import org.jspecify.annotations.Nullable;

import io.jstach.rainbowgum.LogMetrics.Gauge;
import io.jstach.rainbowgum.LogPublisher.AsyncLogPublisher;

/**
 * An experimental bounded async publisher with one output worker. Producers block when
 * the pending buffer is full. Events are delivered in insertion order, including calls
 * already waiting for space when shutdown begins. New calls are rejected during shutdown.
 * <p>
 * Up to {@link Builder#bufferSize(int) bufferSize} events may wait for delivery while
 * another batch of up to that size is being written. The queued metric excludes the batch
 * currently being written. Events passed directly to {@link #log(LogEvent)} must already
 * be {@linkplain LogEvent#freeze() frozen}, as required by async publishers.
 * <p>
 * Closing waits for the configured timeout without interrupting output operations. If it
 * times out, an error alert is recorded and the worker continues draining before closing
 * the appender. Logging recursively from the worker is rejected to prevent deadlock.
 */
public final class CodexAsyncPublisher implements AsyncLogPublisher {

	private final LogAppender appender;

	private final LogAlerts alerts;

	private final Gauge queued;

	private final long shutdownNanos;

	private final Thread worker;

	private final ReentrantLock lock = new ReentrantLock();

	private final Condition readable = lock.newCondition();

	private final Condition writable = lock.newCondition();

	private final Condition terminated = lock.newCondition();

	// All mutable queue and lifecycle state is protected by lock.
	private LogEvent[] pending;

	private int count;

	private int producers;

	private State state = State.NEW;

	private enum State {

		NEW, STARTING, RUNNING, CLOSING, FAILED, CLOSED

	}

	// The method reference is invoked only by start(), after construction has finished.
	@SuppressWarnings("methodref.receiver.bound")
	private CodexAsyncPublisher(String name, LogConfig config, LogAppender appender, int capacity, long shutdownNanos) {
		this.appender = appender;
		this.alerts = config.alerts();
		this.queued = config.metrics().gauge(LogMetrics.EVENTS_QUEUED_METRIC, Level.INFO);
		this.pending = new LogEvent[capacity];
		this.shutdownNanos = shutdownNanos;
		this.worker = new Thread(this::consume, "rainbowgum-codex-async-" + name);
		this.worker.setDaemon(true);
	}

	/**
	 * Creates a builder. Select its factory with
	 * {@link LogRouter.Router.Builder#publisher(LogPublisher.PublisherFactory)}.
	 * @return builder.
	 */
	public static Builder builder() {
		return new Builder();
	}

	/**
	 * Builds a publisher factory. Options are programmatic and do not read properties.
	 */
	public static final class Builder extends LogPublisher.AbstractBuilder<Builder> {

		private int bufferSize = 1024;

		private long shutdownNanos = Duration.ofSeconds(10).toNanos();

		private Builder() {
		}

		/**
		 * Sets the maximum number of pending events, excluding the batch being written.
		 * @param bufferSize positive capacity, defaults to 1024.
		 * @return this builder.
		 */
		public Builder bufferSize(int bufferSize) {
			if (bufferSize < 1) {
				throw new IllegalArgumentException("bufferSize must be positive.");
			}
			this.bufferSize = bufferSize;
			return this;
		}

		/**
		 * Sets how long each close call waits for delivery and appender closure.
		 * @param timeout nonnegative timeout, defaults to ten seconds. Zero does not
		 * wait.
		 * @return this builder.
		 */
		public Builder shutdownTimeout(Duration timeout) {
			if (timeout.isNegative()) {
				throw new IllegalArgumentException("shutdownTimeout must not be negative.");
			}
			this.shutdownNanos = timeout.toNanos();
			return this;
		}

		@Override
		protected Builder self() {
			return this;
		}

		@Override
		public PublisherFactory build() {
			int capacity = bufferSize;
			long timeout = shutdownNanos;
			return (name, config, appenders) -> new CodexAsyncPublisher(name, config, appenders.asSingle(), capacity,
					timeout);
		}

	}

	@Override
	public void start(LogConfig config) {
		lock.lock();
		try {
			if (state == State.RUNNING) {
				return;
			}
			if (state != State.NEW) {
				throw new IllegalStateException("CodexAsyncPublisher cannot be restarted.");
			}
			state = State.STARTING;
			try {
				appender.start(config);
				state = State.RUNNING;
				worker.start();
			}
			catch (RuntimeException | Error e) {
				state = State.CLOSED;
				try {
					appender.close();
				}
				catch (RuntimeException | Error closeFailure) {
					e.addSuppressed(closeFailure);
				}
				throw e;
			}
		}
		finally {
			lock.unlock();
		}
	}

	@Override
	public void log(LogEvent event) {
		Objects.requireNonNull(event);
		if (Thread.currentThread().equals(worker)) {
			throw new IllegalStateException("Cannot publish recursively from the async worker.");
		}
		try {
			lock.lockInterruptibly();
			try {
				if (state != State.RUNNING) {
					throw new IllegalStateException("CodexAsyncPublisher is not running.");
				}
				producers++;
				try {
					while (count == pending.length && state != State.FAILED && state != State.CLOSED) {
						writable.await();
					}
					if (state == State.FAILED || state == State.CLOSED) {
						throw new IllegalStateException(
								"CodexAsyncPublisher worker stopped before accepting the event.");
					}
					pending[count++] = event;
					queued.increment();
					if (count == 1) {
						readable.signal();
					}
				}
				finally {
					producers--;
					// Pass available space to one waiter, including when this caller
					// was interrupted. Avoid waking a herd that races to refill a batch.
					if (producers != 0 && count < pending.length) {
						writable.signal();
					}
					if (state == State.CLOSING && producers == 0) {
						readable.signal();
					}
				}
			}
			finally {
				lock.unlock();
			}
		}
		catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			alerts.error(CodexAsyncPublisher.class, "Interrupted while waiting to publish an event.", e);
		}
	}

	private void consume() {
		try {
			LogEvent[] batch = new LogEvent[pending.length];
			while (true) {
				int size;
				lock.lock();
				try {
					while (count == 0) {
						if (state == State.CLOSING && producers == 0) {
							return;
						}
						readable.awaitUninterruptibly();
					}
					// Exchange ownership, without copying event references under the
					// lock.
					LogEvent[] empty = batch;
					batch = pending;
					pending = empty;
					size = count;
					count = 0;
					queued.decrement(size);
					writable.signal();
				}
				finally {
					lock.unlock();
				}
				try {
					appender.append(batch, size);
				}
				finally {
					Arrays.fill(batch, 0, size, null);
				}
			}
		}
		catch (RuntimeException | Error e) {
			alerts.error(CodexAsyncPublisher.class, "Async worker failed.", e);
		}
		finally {
			finish();
		}
	}

	private void finish() {
		lock.lock();
		try {
			state = State.FAILED;
			queued.decrement(count);
			Arrays.fill(pending, null);
			count = 0;
			writable.signalAll();
		}
		finally {
			lock.unlock();
		}
		try {
			appender.close();
		}
		catch (RuntimeException | Error e) {
			alerts.error(CodexAsyncPublisher.class, "Failed to close async appender.", e);
		}
		finally {
			lock.lock();
			try {
				state = State.CLOSED;
				terminated.signalAll();
			}
			finally {
				lock.unlock();
			}
		}
	}

	@Override
	public void close() {
		boolean interrupted = Thread.interrupted();
		boolean closeUnstarted = false;
		boolean timedOut = false;
		@Nullable InterruptedException interruptedWait = null;
		lock.lock();
		try {
			if (state == State.CLOSED) {
				return;
			}
			if (state == State.STARTING) {
				throw new IllegalStateException("Cannot close CodexAsyncPublisher during appender startup.");
			}
			if (state == State.NEW) {
				state = State.CLOSING;
				closeUnstarted = true;
			}
			else {
				if (state == State.RUNNING) {
					state = State.CLOSING;
					readable.signal();
				}
				if (Thread.currentThread().equals(worker)) {
					return;
				}
				long remaining = shutdownNanos;
				while (state != State.CLOSED && remaining > 0) {
					try {
						remaining = terminated.awaitNanos(remaining);
					}
					catch (InterruptedException e) {
						interrupted = true;
						interruptedWait = e;
						break;
					}
				}
				timedOut = state != State.CLOSED;
			}
		}
		finally {
			lock.unlock();
			if (interrupted) {
				Thread.currentThread().interrupt();
			}
		}
		if (closeUnstarted) {
			finish();
		}
		else if (interruptedWait != null) {
			alerts.error(CodexAsyncPublisher.class, "Interrupted while waiting for async publisher shutdown.",
					interruptedWait);
		}
		else if (timedOut) {
			alerts.error(CodexAsyncPublisher.class, "Async publisher is still draining after close.",
					new TimeoutException("Shutdown wait ended before the appender closed."));
		}
	}

}

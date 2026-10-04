package io.jstach.rainbowgum;

import java.lang.System.Logger.Level;
import java.time.Duration;
import java.util.Arrays;
import java.util.Objects;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;

import org.jspecify.annotations.Nullable;

import io.jstach.rainbowgum.LogMetrics.Gauge;
import io.jstach.rainbowgum.LogPublisher.AsyncLogPublisher;

/**
 * The default bounded async publisher with one output worker. Producers block when the
 * pending buffer is full. Events are delivered in insertion order, including calls
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
 * Rejected events increment {@link LogMetrics#EVENTS_DROPPED_METRIC}, with one error
 * alert per rejection reason for each publisher. An interrupted producer can enqueue when
 * space is available; interruption while waiting for space drops the event and preserves
 * the interrupt flag.
 */
public final class BatchSwapAsyncLogPublisher implements AsyncLogPublisher {

	private final String name;

	private final LogMetrics metrics;

	private final AtomicInteger alertedReasons = new AtomicInteger();

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
	private BatchSwapAsyncLogPublisher(String name, LogConfig config, LogAppender appender, int capacity,
			long shutdownNanos) {
		this.name = name;
		this.metrics = config.metrics();
		this.appender = appender;
		this.alerts = config.alerts();
		this.queued = config.metrics().gauge(LogMetrics.EVENTS_QUEUED_METRIC, Level.INFO);
		this.pending = new LogEvent[capacity];
		this.shutdownNanos = shutdownNanos;
		this.worker = new Thread(this::consume, "rainbowgum-batch-swap-async-" + name);
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
			return (name, config, appenders) -> new BatchSwapAsyncLogPublisher(name, config, appenders.asSingle(),
					capacity, timeout);
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
				throw new IllegalStateException("BatchSwapAsyncLogPublisher cannot be restarted.");
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
		@Nullable Drop reason;
		if (Thread.currentThread().equals(worker)) {
			reason = Drop.REENTRANT;
		}
		else {
			lock.lock();
			try {
				reason = enqueueOrNull(event);
			}
			finally {
				lock.unlock();
			}
		}
		// Alert listeners can themselves log. Never call them under the queue lock.
		if (reason != null) {
			dropped(reason, 1);
		}
	}

	// Called with lock held. Null means the event was accepted.
	private @Nullable Drop enqueueOrNull(LogEvent event) {
		if (state != State.RUNNING) {
			return switch (state) {
				case NEW, STARTING -> Drop.NOT_STARTED;
				case FAILED -> Drop.WORKER_FAILED;
				default -> Drop.CLOSED;
			};
		}
		producers++;
		try {
			while (count == pending.length && state != State.FAILED && state != State.CLOSED) {
				writable.await();
			}
			if (state == State.FAILED || state == State.CLOSED) {
				return Drop.WORKER_FAILED;
			}
			pending[count++] = event;
			queued.increment();
			if (count == 1) {
				readable.signal();
			}
			return null;
		}
		catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			return Drop.INTERRUPTED;
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

	private enum Drop {

		NOT_STARTED(1 << 0, "the publisher has not been started"), CLOSED(1 << 1, "the publisher is closing or closed"),
		INTERRUPTED(1 << 2, "the logging thread was interrupted while the buffer was full"),
		REENTRANT(1 << 3, "the publisher's own thread logged recursively"),
		WORKER_FAILED(1 << 4, "the worker stopped before delivering the event");

		final String description;

		final int bit;

		Drop(int bit, String description) {
			this.description = description;
			this.bit = bit;
		}

	}

	private void dropped(Drop reason, int size) {
		metrics.errorCounter(LogMetrics.EVENTS_DROPPED_METRIC, size);
		int previous;
		do {
			previous = alertedReasons.get();
			if ((previous & reason.bit) != 0) {
				return;
			}
		}
		while (!alertedReasons.compareAndSet(previous, previous | reason.bit));
		alerts.error(BatchSwapAsyncLogPublisher.class,
				new IllegalStateException("Async publisher '" + name + "' dropped " + size + " event(s): "
						+ reason.description + ". Further drops for this reason are only counted in "
						+ LogMetrics.EVENTS_DROPPED_METRIC + "."));
	}

	private void consume() {
		@Nullable Throwable failure = null;
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
				catch (RuntimeException | Error e) {
					metrics.errorCounter(LogMetrics.EVENTS_FAILED_METRIC, size);
					throw e;
				}
				finally {
					Arrays.fill(batch, 0, size, null);
				}
			}
		}
		catch (RuntimeException | Error e) {
			failure = e;
		}
		finally {
			finish(failure);
		}
	}

	private void finish(@Nullable Throwable failure) {
		int discarded;
		lock.lock();
		try {
			state = failure == null ? State.CLOSING : State.FAILED;
			discarded = count;
			queued.decrement(count);
			Arrays.fill(pending, null);
			count = 0;
			writable.signalAll();
		}
		finally {
			lock.unlock();
		}
		try {
			// Stop admission and release waiters before notifying listeners.
			if (failure != null) {
				alerts.error(BatchSwapAsyncLogPublisher.class, "Async worker failed.", failure);
			}
			if (discarded != 0) {
				dropped(Drop.WORKER_FAILED, discarded);
			}
		}
		finally {
			closeAppender();
		}
	}

	private void closeAppender() {
		try {
			appender.close();
		}
		catch (RuntimeException | Error e) {
			alerts.error(BatchSwapAsyncLogPublisher.class, "Failed to close async appender.", e);
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
				throw new IllegalStateException("Cannot close BatchSwapAsyncLogPublisher during appender startup.");
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
			finish(null);
		}
		else if (interruptedWait != null) {
			alerts.error(BatchSwapAsyncLogPublisher.class, "Interrupted while waiting for async publisher shutdown.",
					interruptedWait);
		}
		else if (timedOut) {
			alerts.error(BatchSwapAsyncLogPublisher.class, "Async publisher is still draining after close.",
					new TimeoutException("Shutdown wait ended before the appender closed."));
		}
	}

}

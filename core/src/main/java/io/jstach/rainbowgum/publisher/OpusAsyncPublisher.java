package io.jstach.rainbowgum.publisher;

import java.time.Duration;
import java.util.Arrays;
import java.util.Objects;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;

import org.jspecify.annotations.Nullable;

import io.jstach.rainbowgum.LogAlerts;
import io.jstach.rainbowgum.LogAppender;
import io.jstach.rainbowgum.LogConfig;
import io.jstach.rainbowgum.LogEvent;
import io.jstach.rainbowgum.LogMetrics;
import io.jstach.rainbowgum.LogMetrics.Gauge;
import io.jstach.rainbowgum.LogMetrics.StandardMetric;
import io.jstach.rainbowgum.LogPublisher;
import io.jstach.rainbowgum.LogPublisherRegistry;

/**
 * An asynchronous publisher: logging threads put events on a bounded queue and a single
 * consumer thread appends them in batches, in the order they were accepted.
 * <p>
 * Behavior:
 * <ul>
 * <li>When the queue is full a logging thread waits for space, so no event is lost to
 * back pressure.</li>
 * <li>{@link #log(LogEvent)} never throws. An event that cannot be accepted is dropped
 * and counted in {@link LogMetrics#EVENTS_DROPPED_METRIC}, with one alert per reason:
 * after {@link #close()}, when the waiting logging thread is interrupted, when the queue
 * is full before {@link #start(LogConfig)}, and when the consumer thread itself logs
 * while the queue is full (waiting there would deadlock).</li>
 * <li>The consumer thread is never interrupted, so outputs are not interrupted in the
 * middle of I/O, and it survives anything an appender throws.</li>
 * <li>{@link LogMetrics.StandardMetric#EVENTS_QUEUED} reports how many accepted events
 * are waiting to be appended.</li>
 * <li>{@link #close()} stops accepting events and waits up to the shutdown timeout for
 * every accepted event to be appended before the appender is closed. If that does not
 * finish in time, or the closing thread is interrupted, an error alert reports how many
 * events are still queued.</li>
 * </ul>
 * Events must be safe to use on another thread (see {@link LogEvent#freeze()}), which the
 * router guarantees for asynchronous publishers.
 */
public final class OpusAsyncPublisher implements LogPublisher.AsyncLogPublisher {

	/**
	 * Default time {@link #close()} waits for accepted events to be appended.
	 */
	public static final Duration DEFAULT_SHUTDOWN_TIMEOUT = Duration.ofSeconds(10);

	private static final int NEW = 0;

	private static final int RUNNING = 1;

	private static final int CLOSING = 2;

	private static final int TERMINATED = 3;

	private final String name;

	private final LogAppender appender;

	private final LogAlerts alerts;

	private final LogMetrics metrics;

	private final Duration shutdownTimeout;

	private final ReentrantLock lock = new ReentrantLock();

	private final Condition notEmpty = lock.newCondition();

	private final Condition notFull = lock.newCondition();

	// Ring buffer and state, guarded by lock.
	private final @Nullable LogEvent[] ring;

	private int head;

	private int count;

	private int state = NEW;

	private @Nullable Thread consumer;

	// Created at start; until then accepted events are only counted in the ring.
	private Gauge eventsQueued = Gauge.noop();

	private final CountDownLatch terminated = new CountDownLatch(1);

	private final AtomicInteger alertedReasons = new AtomicInteger();

	private OpusAsyncPublisher(String name, LogAppender appender, int bufferSize, Duration shutdownTimeout,
			LogAlerts alerts, LogMetrics metrics) {
		this.name = name;
		this.appender = appender;
		this.ring = new LogEvent[bufferSize];
		this.shutdownTimeout = shutdownTimeout;
		this.alerts = alerts;
		this.metrics = metrics;
	}

	/**
	 * Creates a builder.
	 * @return builder.
	 */
	public static Builder builder() {
		return new Builder();
	}

	/**
	 * Builds a {@link PublisherFactory} for {@link OpusAsyncPublisher}.
	 */
	public static final class Builder {

		private int bufferSize = LogPublisherRegistry.ASYNC_BUFFER_SIZE;

		private Duration shutdownTimeout = DEFAULT_SHUTDOWN_TIMEOUT;

		private Builder() {
		}

		/**
		 * Queue capacity, which is also the largest batch handed to the appender. Default
		 * {@value LogPublisherRegistry#ASYNC_BUFFER_SIZE}.
		 * @param bufferSize positive capacity.
		 * @return this.
		 */
		public Builder bufferSize(int bufferSize) {
			if (bufferSize <= 0) {
				throw new IllegalArgumentException("bufferSize must be positive: " + bufferSize);
			}
			this.bufferSize = bufferSize;
			return this;
		}

		/**
		 * How long {@link OpusAsyncPublisher#close()} waits for accepted events to be
		 * appended. Default 10 seconds.
		 * @param shutdownTimeout timeout, not negative.
		 * @return this.
		 */
		public Builder shutdownTimeout(Duration shutdownTimeout) {
			if (shutdownTimeout.isNegative()) {
				throw new IllegalArgumentException("shutdownTimeout must not be negative: " + shutdownTimeout);
			}
			this.shutdownTimeout = shutdownTimeout;
			return this;
		}

		/**
		 * Creates the publisher factory. Each publisher appends to the route's appenders
		 * combined into one.
		 * @return publisher factory.
		 */
		public PublisherFactory build() {
			int b = bufferSize;
			Duration t = shutdownTimeout;
			return (name, config, appenders) -> new OpusAsyncPublisher(name, appenders.asSingle(), b, t,
					config.alerts(), config.metrics());
		}

	}

	@Override
	public void start(LogConfig config) {
		Thread thread;
		lock.lock();
		try {
			if (state != NEW) {
				throw new IllegalStateException("OpusAsyncPublisher '" + name + "' can only be started once");
			}
			eventsQueued = metrics.gauge(StandardMetric.EVENTS_QUEUED.metricName(),
					StandardMetric.EVENTS_QUEUED.level());
			eventsQueued.increment(count);
			thread = new Thread(this::consume, "rainbowgum-async-" + name);
			thread.setDaemon(true);
			consumer = thread;
			state = RUNNING;
		}
		finally {
			lock.unlock();
		}
		try {
			appender.start(config);
		}
		catch (RuntimeException | Error e) {
			// Release anyone waiting: without a consumer nothing would ever make room.
			lock.lock();
			try {
				state = TERMINATED;
				consumer = null;
				discardQueued();
				notFull.signalAll();
			}
			finally {
				lock.unlock();
			}
			terminated.countDown();
			throw e;
		}
		thread.start();
	}

	@Override
	public void log(LogEvent event) {
		Drop drop;
		lock.lock();
		try {
			drop = offer(event);
		}
		finally {
			lock.unlock();
		}
		if (drop != null) {
			dropped(drop, 1);
		}
	}

	/*
	 * Called with the lock held. Returns why the event was dropped, or null if accepted.
	 */
	private @Nullable Drop offer(LogEvent event) {
		boolean onConsumer = Thread.currentThread().equals(consumer);
		while (count == ring.length) {
			if (state == NEW) {
				return Drop.NOT_STARTED_FULL;
			}
			if (state != RUNNING) {
				return Drop.CLOSED;
			}
			if (onConsumer) {
				return Drop.CONSUMER_FULL;
			}
			try {
				notFull.await();
			}
			catch (InterruptedException e) {
				Thread.currentThread().interrupt();
				return Drop.INTERRUPTED;
			}
		}
		if (state != NEW && state != RUNNING) {
			return Drop.CLOSED;
		}
		ring[(head + count) % ring.length] = event;
		count++;
		eventsQueued.increment();
		if (count == 1) {
			notEmpty.signal();
		}
		return null;
	}

	private void consume() {
		final LogEvent[] batch = new LogEvent[ring.length];
		try {
			while (true) {
				int n;
				lock.lock();
				try {
					while (count == 0 && state == RUNNING) {
						notEmpty.awaitUninterruptibly();
					}
					if (count == 0) {
						break;
					}
					n = takeBatch(batch);
					notFull.signalAll();
				}
				finally {
					lock.unlock();
				}
				// Nothing interrupts this thread on purpose; never let an output see a
				// flag.
				Thread.interrupted();
				try {
					appender.append(batch, n);
				}
				catch (Throwable e) {
					alerts.error(OpusAsyncPublisher.class,
							"Async publisher '" + name + "' appender failed on a batch of " + n + " events", e);
				}
				finally {
					Arrays.fill(batch, 0, n, null);
				}
			}
		}
		finally {
			terminate();
		}
	}

	/*
	 * Called with the lock held.
	 */
	private int takeBatch(LogEvent[] batch) {
		int n = count;
		for (int i = 0; i < n; i++) {
			int slot = (head + i) % ring.length;
			batch[i] = Objects.requireNonNull(ring[slot]);
			ring[slot] = null;
		}
		head = (head + n) % ring.length;
		count = 0;
		eventsQueued.decrement(n);
		return n;
	}

	private void terminate() {
		int leftover;
		lock.lock();
		try {
			state = TERMINATED;
			leftover = discardQueued();
			notFull.signalAll();
		}
		finally {
			lock.unlock();
		}
		try {
			if (leftover > 0) {
				dropped(Drop.CLOSED, leftover);
			}
			Thread.interrupted();
			appender.close();
		}
		catch (Throwable e) {
			alerts.error(OpusAsyncPublisher.class, "Async publisher '" + name + "' failed to close its appender", e);
		}
		finally {
			terminated.countDown();
		}
	}

	/*
	 * Called with the lock held.
	 */
	private int discardQueued() {
		int n = count;
		for (int i = 0; i < n; i++) {
			ring[(head + i) % ring.length] = null;
		}
		count = 0;
		head = 0;
		eventsQueued.decrement(n);
		return n;
	}

	@Override
	public void close() {
		Thread c;
		int never = -1;
		lock.lock();
		try {
			if (state == CLOSING || state == TERMINATED) {
				return;
			}
			c = consumer;
			if (c == null) {
				state = TERMINATED;
				never = discardQueued();
				notFull.signalAll();
			}
			else {
				state = CLOSING;
				notEmpty.signalAll();
				notFull.signalAll();
			}
		}
		finally {
			lock.unlock();
		}
		if (c == null) {
			if (never > 0) {
				dropped(Drop.CLOSED, never);
			}
			try {
				appender.close();
			}
			finally {
				terminated.countDown();
			}
			return;
		}
		if (Thread.currentThread().equals(c)) {
			// Closed from inside an append: the consumer finishes once this batch
			// returns.
			return;
		}
		awaitTermination();
	}

	private void awaitTermination() {
		try {
			if (terminated.await(shutdownTimeout.toNanos(), TimeUnit.NANOSECONDS)) {
				return;
			}
			alerts.error(OpusAsyncPublisher.class,
					new TimeoutException("Async publisher '" + name + "' did not finish within "
							+ shutdownTimeout.toMillis() + "ms of close; " + queued() + " events still queued"));
		}
		catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			alerts.error(OpusAsyncPublisher.class, "Async publisher '" + name
					+ "' close was interrupted before it finished; " + queued() + " events still queued", e);
		}
	}

	private int queued() {
		lock.lock();
		try {
			return count;
		}
		finally {
			lock.unlock();
		}
	}

	private void dropped(Drop reason, int n) {
		metrics.errorCounter(LogMetrics.EVENTS_DROPPED_METRIC, n);
		int bit = reason.bit;
		int seen;
		do {
			seen = alertedReasons.get();
			if ((seen & bit) != 0) {
				return;
			}
		}
		while (!alertedReasons.compareAndSet(seen, seen | bit));
		alerts.error(OpusAsyncPublisher.class,
				new IllegalStateException("Async publisher '" + name + "' dropped " + n + " event(s): "
						+ reason.description + ". Further drops for this reason are only counted in "
						+ LogMetrics.EVENTS_DROPPED_METRIC));
	}

	private enum Drop {

		CLOSED(1, "the publisher is closed"),
		INTERRUPTED(2, "the logging thread was interrupted while the queue was full"),
		NOT_STARTED_FULL(4, "the queue is full and the publisher has not been started"),
		CONSUMER_FULL(8, "the publisher's own thread logged while the queue was full");

		final int bit;

		final String description;

		Drop(int bit, String description) {
			this.bit = bit;
			this.description = description;
		}

	}

	@Override
	public String toString() {
		return "OpusAsyncPublisher[name=" + name + ", bufferSize=" + ring.length + ", shutdownTimeout="
				+ shutdownTimeout + ", appender=" + appender + "]";
	}

}

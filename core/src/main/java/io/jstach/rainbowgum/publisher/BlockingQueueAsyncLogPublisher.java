package io.jstach.rainbowgum.publisher;

import java.lang.System.Logger.Level;
import java.time.Duration;
import java.time.Instant;
import java.util.AbstractCollection;
import java.util.Iterator;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;

import io.jstach.rainbowgum.KeyValues;
import io.jstach.rainbowgum.LogAlerts;
import io.jstach.rainbowgum.LogAppender;
import io.jstach.rainbowgum.LogConfig;
import io.jstach.rainbowgum.LogEvent;
import io.jstach.rainbowgum.LogMetrics.Gauge;
import io.jstach.rainbowgum.LogMetrics.StandardMetric;
import io.jstach.rainbowgum.LogPublisher;
import io.jstach.rainbowgum.LogPublisherRegistry;

/**
 * An async publisher that uses a blocking queue and a single thread consumer.
 * <p>
 * {@link #close()} stops accepting new calls and waits up to the shutdown timeout for
 * queued events and calls already waiting to enqueue to be written, then closes the
 * appender. The worker thread is never interrupted, so outputs are not interrupted in the
 * middle of I/O.
 */
public final class BlockingQueueAsyncLogPublisher implements LogPublisher.AsyncLogPublisher {

	private final BlockingQueue<LogEvent> queue;

	private final LogAppender appender;

	private volatile boolean running = false;

	private final AtomicInteger activeProducers = new AtomicInteger();

	private final int bufferSize;

	private final Worker worker;

	private final LogAlerts alerts;

	private Gauge eventsQueued = Gauge.noop();

	private final Duration shutdownTimeout;

	/*
	 * Queued by close() to wake the worker instead of interrupting it, which would land
	 * in the middle of whatever output I/O the worker is doing.
	 */
	private static final LogEvent STOP = LogEvent.of(Instant.EPOCH, "", 0, Level.OFF, "", "", KeyValues.of(), null);

	/**
	 * Creates the publisher with its own standalone {@link LogConfig} (not one shared
	 * with the rest of the application) for its {@link LogAlerts} - mainly useful
	 * standalone/in tests. Prefer {@link #of(LogAppender, int, LogAlerts)} when a
	 * {@link LogConfig} is already available.
	 * @param appender appenders.
	 * @param bufferSize the queue size.
	 * @return async publisher.
	 */
	public static BlockingQueueAsyncLogPublisher of(LogAppender appender, int bufferSize) {
		return of(appender, bufferSize, LogConfig.builder().build().alerts());
	}

	/**
	 * Creates the publisher.
	 * @param appender appenders.
	 * @param bufferSize the queue size.
	 * @param alerts alerts for reporting internal errors (interrupted queue puts, worker
	 * failures).
	 * @return async publisher.
	 */
	public static BlockingQueueAsyncLogPublisher of(LogAppender appender, int bufferSize, LogAlerts alerts) {
		return of(appender, bufferSize, alerts, Duration.ofMillis(LogPublisherRegistry.ASYNC_SHUTDOWN_TIMEOUT));
	}

	/**
	 * Creates the publisher.
	 * @param appender appenders.
	 * @param bufferSize the queue size.
	 * @param alerts alerts for reporting internal errors (interrupted queue puts, worker
	 * failures, and a shutdown that did not finish in time).
	 * @param shutdownTimeout how long {@link #close()} waits for queued events to be
	 * written.
	 * @return async publisher.
	 */
	public static BlockingQueueAsyncLogPublisher of(LogAppender appender, int bufferSize, LogAlerts alerts,
			Duration shutdownTimeout) {
		if (bufferSize <= 0) {
			throw new IllegalArgumentException("buffer size should be greater than 0");
		}
		if (shutdownTimeout.isNegative()) {
			throw new IllegalArgumentException("shutdown timeout should not be negative");
		}
		BlockingQueue<LogEvent> queue = new ArrayBlockingQueue<>(bufferSize);
		return new BlockingQueueAsyncLogPublisher(appender, queue, bufferSize, alerts, shutdownTimeout);
	}

	private BlockingQueueAsyncLogPublisher(LogAppender appender, BlockingQueue<LogEvent> queue, int bufferSize,
			LogAlerts alerts, Duration shutdownTimeout) {
		super();
		this.appender = appender;
		this.queue = queue;
		this.bufferSize = bufferSize;
		this.worker = new Worker();
		this.alerts = alerts;
		this.shutdownTimeout = shutdownTimeout;
	}

	@Override
	public void log(LogEvent event) {
		if (!running) {
			throw new IllegalStateException();
		}
		activeProducers.incrementAndGet();
		try {
			// Close may have begun between the first check and registration.
			if (!running) {
				throw new IllegalStateException();
			}
			queue.put(event);
			eventsQueued.increment();
		}
		catch (InterruptedException e) {
			alerts.error(BlockingQueueAsyncLogPublisher.class, e);
			Thread.currentThread().interrupt();
		}
		finally {
			if (activeProducers.decrementAndGet() == 0 && !running) {
				// Wake a shutdown drain waiting for the last producer, even if its
				// put was interrupted. A full queue already gives the worker work.
				queue.offer(STOP);
			}
		}
	}

	@Override
	public void close() {
		if (!running) {
			return;
		}
		running = false;
		var tool = new InterruptUtil();
		try {
			tool.maskInterruptFlag();
			long deadline = System.nanoTime() + shutdownTimeout.toNanos();
			/*
			 * A full queue already gives the worker work. Do not wait for space or allow
			 * an interrupted closer to skip waking an idle worker.
			 */
			queue.offer(STOP);
			long remainingNanos = deadline - System.nanoTime();
			if (remainingNanos > 0) {
				TimeUnit.NANOSECONDS.timedJoin(worker, remainingNanos);
			}
		}
		catch (InterruptedException e) {
			alerts.error(BlockingQueueAsyncLogPublisher.class, e);
			Thread.currentThread().interrupt();
			return;
		}
		finally {
			tool.unmaskInterruptFlag();
		}
		if (worker.isAlive()) {
			alerts.error(BlockingQueueAsyncLogPublisher.class,
					new TimeoutException("Async publisher did not finish writing within " + shutdownTimeout.toMillis()
							+ "ms of close; " + queuedEvents() + " events still queued"));
		}
	}

	@SuppressWarnings("ReferenceEquality") // STOP is a private marker instance
	private long queuedEvents() {
		return queue.stream().filter(e -> e != STOP).count();
	}

	private void _close() {
		appender.close();
	}

	@Override
	public void start(LogConfig config) {
		// The worker thread can only run once, so this also rejects start after close.
		if (running || worker.getState() != Thread.State.NEW) {
			throw new IllegalStateException("BlockingQueueAsyncLogPublisher can only be started once");
		}

		// Like the synchronous publisher, the publisher owns starting its appender.
		appender.start(config);
		// Publish the gauge before enabling producers and starting the worker.
		eventsQueued = config.metrics()
			.gauge(StandardMetric.EVENTS_QUEUED.metricName(), StandardMetric.EVENTS_QUEUED.level());
		worker.setDaemon(true);
		worker.setName(BlockingQueueAsyncLogPublisher.class.getSimpleName());
		running = true;
		worker.start();
	}

	@SuppressWarnings("null") // TODO eclipse bug
	void append(LogEvent[] events, int count) {
		appender.append(events, count);
	}

	class Worker extends Thread {

		final LogEvent[] buffer = new LogEvent[bufferSize];

		final FakeCollection fake = new FakeCollection();

		private boolean stopRequested;

		@Override
		public void run() {
			try {
				while (running && !stopRequested) {
					try {
						var event = queue.take();
						fake.add(event);
						drain();
					}
					catch (InterruptedException e) {
						// Shutdown never interrupts the worker, so this came from
						// elsewhere.
						alerts.error(BlockingQueueAsyncLogPublisher.class, e);
						break;
					}
					catch (Exception e) {
						alerts.error(BlockingQueueAsyncLogPublisher.class, e);
					}
				}
				running = false;
				drainRemaining();
			}
			finally {
				_close();
			}
		}

		private int drain() {
			try {
				int size = fake.size;
				int added = queue.drainTo(fake, bufferSize - size);
				if (fake.size > 0) {
					eventsQueued.decrement(fake.size);
					append(buffer, fake.size);
				}
				return added;
			}
			finally {
				fake.reset();
			}
		}

		/*
		 * Everything still queued at shutdown, including events from producers that were
		 * blocked on a full queue when close began.
		 */
		private void drainRemaining() {
			while (true) {
				try {
					/*
					 * Check producer completion before the final drain: a producer can
					 * enqueue and finish between an empty drain and a later check. Once
					 * close has stopped admission and this count is zero, no producer can
					 * enqueue another event.
					 */
					if (activeProducers.get() != 0) {
						fake.add(queue.take());
						drain();
					}
					else if (drain() == 0) {
						return;
					}
				}
				catch (Exception e) {
					alerts.error(BlockingQueueAsyncLogPublisher.class, e);
				}
			}
		}

		class FakeCollection extends AbstractCollection<LogEvent> {

			private int size = 0;

			@Override
			@SuppressWarnings("ReferenceEquality") // STOP is a private marker instance
			public boolean add(LogEvent e) {
				if (e == STOP) {
					stopRequested = true;
					return false;
				}
				buffer[size] = e;
				size++;
				return true;
			}

			@Override
			public Iterator<LogEvent> iterator() {
				throw new UnsupportedOperationException();
			}

			@Override
			public int size() {
				return size;
			}

			void reset() {
				size = 0;
			}

		}

	}

}

class InterruptUtil {

	final boolean previouslyInterrupted;

	InterruptUtil() {
		super();
		previouslyInterrupted = Thread.currentThread().isInterrupted();
	}

	public void maskInterruptFlag() {
		if (previouslyInterrupted) {
			Thread.interrupted();
		}
	}

	public void unmaskInterruptFlag() {
		if (previouslyInterrupted) {
			Thread.currentThread().interrupt();
		}
	}

}

package io.jstach.rainbowgum.publisher;

import java.util.AbstractCollection;
import java.util.Iterator;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;

import io.jstach.rainbowgum.LogAppender;
import io.jstach.rainbowgum.LogConfig;
import io.jstach.rainbowgum.LogEvent;
import io.jstach.rainbowgum.LogPublisher;
import io.jstach.rainbowgum.MetaLog;
import io.jstach.rainbowgum.LogResponse.Status;

/**
 * An async publisher that uses a blocking queue and a single thread consumer.
 */
public final class BlockingQueueAsyncLogPublisher implements LogPublisher.AsyncLogPublisher {

	private final BlockingQueue<LogEvent> queue;

	private final LogAppender appender;

	private volatile boolean running = false;

	private final int bufferSize;

	private final Worker worker;

	/**
	 * Creates the publisher.
	 * @param appender appenders.
	 * @param bufferSize the queue size.
	 * @return async publisher.
	 */
	public static BlockingQueueAsyncLogPublisher of(LogAppender appender, int bufferSize) {
		BlockingQueue<LogEvent> queue = new ArrayBlockingQueue<>(bufferSize);
		return new BlockingQueueAsyncLogPublisher(appender, queue, bufferSize);
	}

	private BlockingQueueAsyncLogPublisher(LogAppender appender, BlockingQueue<LogEvent> queue, int bufferSize) {
		super();
		if (bufferSize <= 0) {
			throw new IllegalArgumentException("buffer size should be greater than 0");
		}
		this.appender = appender;
		this.queue = queue;
		this.bufferSize = bufferSize;
		this.worker = new Worker();
	}

	@Override
	public Status status() throws Exception {
		return new Status.QueueStatus(queue.size(), bufferSize);
	}

	@Override
	public void log(LogEvent event) {
		if (!running) {
			throw new IllegalStateException();
		}
		try {
			queue.put(event);
		}
		catch (InterruptedException e) {
			MetaLog.error(BlockingQueueAsyncLogPublisher.class, e);
			Thread.currentThread().interrupt();

		}
	}

	@Override
	public void close() {
		running = false;
		worker.interrupt();
		var tool = new InterruptUtil();
		try {
			tool.maskInterruptFlag();
			worker.join(1000);
		}
		catch (InterruptedException e) {
			MetaLog.error(BlockingQueueAsyncLogPublisher.class, e);
		}
		finally {
			tool.unmaskInterruptFlag();
		}
	}

	private void _close() {
		appender.close();
	}

	@Override
	public void start(LogConfig config) {
		if (running) {
			throw new IllegalStateException();
		}

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

		@Override
		public void run() {
			while (running) {
				try {
					var event = queue.take();
					fake.add(event);
					drain();
					// int added = drain();
					// if (added == 0) {
					// pauseStrategy.pause();
					// }
				}
				catch (InterruptedException e) {
					break;
				}
				catch (Exception e) {
					MetaLog.error(BlockingQueueAsyncLogPublisher.class, e);
				}
			}
			drain();
			_close();

		}

		private int drain() {
			try {
				int size = fake.size;
				int added = queue.drainTo(fake, bufferSize - size);
				append(buffer, fake.size);
				return added;
			}
			finally {
				fake.reset();
			}
		}

		class FakeCollection extends AbstractCollection<LogEvent> {

			private int size = 0;

			@Override
			public boolean add(LogEvent e) {
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

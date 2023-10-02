package io.jstach.rainbowgum.router;

import java.lang.System.Logger.Level;
import java.util.AbstractCollection;
import java.util.Iterator;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;

import io.jstach.rainbowgum.Errors;
import io.jstach.rainbowgum.LevelResolver;
import io.jstach.rainbowgum.LogAppender;
import io.jstach.rainbowgum.LogConfig;
import io.jstach.rainbowgum.LogEvent;
import io.jstach.rainbowgum.LogRouter.AsyncLogRouter;

public final class BlockingQueueRouter implements AsyncLogRouter {

	private final BlockingQueue<LogEvent> queue;

	private final LogAppender appender;

	private final LevelResolver levelResolver;

	private volatile boolean running = false;

	private final int bufferSize;

	private final Worker worker;

	public static BlockingQueueRouter of(LogAppender appender, LevelResolver levelResolver, int bufferSize) {
		BlockingQueue<LogEvent> queue = new ArrayBlockingQueue<>(bufferSize);
		return new BlockingQueueRouter(appender, levelResolver, queue, bufferSize);
	}

	private BlockingQueueRouter(LogAppender appender, LevelResolver levelResolver, BlockingQueue<LogEvent> queue,
			int bufferSize) {
		super();
		if (bufferSize <= 0) {
			throw new IllegalArgumentException("buffer size should be greater than 0");
		}
		this.appender = appender;
		this.levelResolver = levelResolver;
		this.queue = queue;
		this.bufferSize = bufferSize;
		this.worker = new Worker();
	}

	@Override
	public LevelResolver levelResolver() {
		return this.levelResolver;
	}

	@Override
	public boolean isEnabled(String loggerName, Level level) {
		return this.levelResolver.isEnabled(loggerName, level);
	}

	@Override
	public void log(LogEvent event) {
		queue.offer(event);
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
			Errors.error(BlockingQueueRouter.class, e);
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
		worker.setName(BlockingQueueRouter.class.getSimpleName());
		running = true;
		worker.start();
	}

	void append(LogEvent[] events, int count) {
		appender.append(events, count);
	}

	class Worker extends Thread {

		final LogEvent[] buffer = new LogEvent[bufferSize];

		final FakeCollection fake = new FakeCollection();

		public void run() {
			while (running) {
				try {
					var event = queue.take();
					fake.add(event);
					drain();
				}
				catch (InterruptedException e) {
					break;
				}
				catch (Exception e) {
					Errors.error(BlockingQueueRouter.class, e);
				}
			}
			drain();
			_close();

		}

		private void drain() {
			try {
				queue.drainTo(fake, bufferSize);
				append(buffer, fake.index);
			}
			finally {
				fake.reset();
			}
		}

		class FakeCollection extends AbstractCollection<LogEvent> {

			private int index = 0;

			@Override
			public boolean add(LogEvent e) {
				buffer[index++] = e;
				return true;
			}

			@Override
			public Iterator<LogEvent> iterator() {
				throw new UnsupportedOperationException();
			}

			@Override
			public int size() {
				return bufferSize;
			}

			void reset() {
				index = 0;
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
			try {
				Thread.currentThread().interrupt();
			}
			catch (SecurityException se) {
				// addError("Failed to interrupt current thread", se);
			}
		}
	}

}

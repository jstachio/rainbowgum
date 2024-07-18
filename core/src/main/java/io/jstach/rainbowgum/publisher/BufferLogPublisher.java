package io.jstach.rainbowgum.publisher;

import java.util.ArrayList;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.SynchronousQueue;
import java.util.concurrent.TimeUnit;

import io.jstach.rainbowgum.LogAppender;
import io.jstach.rainbowgum.LogConfig;
import io.jstach.rainbowgum.LogEvent;
import io.jstach.rainbowgum.LogPublisher;
import io.jstach.rainbowgum.MetaLog;

/**
 * Waits a little for items to collect in a buffer and if buffer is full will signal
 * flush.
 */
public class BufferLogPublisher implements LogPublisher.AsyncLogPublisher {

	private static final int BUFFER_SIZE = 1024 * 100;

	private final BlockingQueue<LogEvent> queue = new ArrayBlockingQueue<>(BUFFER_SIZE);

	private final ExecutorService scheduler = Executors.newSingleThreadExecutor();

	private final SynchronousQueue<Command> command = new SynchronousQueue<>();

	private volatile boolean running = false;

	private final LogAppender appender;

	/**
	 * Add single appender.
	 * @param appender appender.
	 */
	public BufferLogPublisher(LogAppender appender) {
		super();
		this.appender = appender;
	}

	private enum Command {

		RUN;

	}

	@Override
	public void start(LogConfig config) {
		running = true;
		scheduler.execute(() -> {
			while (running) {
				try {
					command.poll(5, TimeUnit.MILLISECONDS);
				}
				catch (InterruptedException e) {
					MetaLog.error(getClass(), e);
				}
				drain();
			}
			drain();
		});

	}

	private void drain() {
		ArrayList<LogEvent> events = new ArrayList<>();
		queue.drainTo(events, BUFFER_SIZE);
		for (var e : events) {
			appender.append(e);
		}
	}

	@Override
	public void log(LogEvent event) {
		if (!queue.offer(event)) {
			command.offer(Command.RUN);
			try {
				queue.put(event);
			}
			catch (InterruptedException e) {
				MetaLog.error(getClass(), e);
			}
		}

	}

	@Override
	public void close() {
		running = false;
		command.offer(Command.RUN);
		scheduler.shutdown();
	}

}

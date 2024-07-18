package io.jstach.rainbowgum.disruptor;

import java.util.EnumSet;
import java.util.concurrent.ThreadFactory;

import org.eclipse.jdt.annotation.Nullable;

import com.lmax.disruptor.BlockingWaitStrategy;
import com.lmax.disruptor.EventHandler;
import com.lmax.disruptor.ExceptionHandler;
import com.lmax.disruptor.RingBuffer;
import com.lmax.disruptor.dsl.Disruptor;
import com.lmax.disruptor.dsl.ProducerType;
import com.lmax.disruptor.util.DaemonThreadFactory;

import io.jstach.rainbowgum.LogAppender;
import io.jstach.rainbowgum.LogConfig;
import io.jstach.rainbowgum.LogEvent;
import io.jstach.rainbowgum.LogPublisher;
import io.jstach.rainbowgum.LogPublisher.AsyncLogPublisher;
import io.jstach.rainbowgum.MetaLog;
import io.jstach.rainbowgum.LogAppender.Appenders;

/**
 * Disruptor async publisher.
 */
public final class DisruptorLogPublisher implements AsyncLogPublisher {

	private final Disruptor<LogEventCell> disruptor;

	private final RingBuffer<LogEventCell> ringBuffer;

	/**
	 * Creates a factory of disruptor log publishers.
	 * @param bufferSize ring buffer size.
	 * @return factory to generate this class.
	 */
	public static PublisherFactory of(int bufferSize) {
		return new PublisherFactory() {
			@Override
			public LogPublisher create(String name, LogConfig config, Appenders appenders) {
				return of(appenders.flags(EnumSet.of(LogAppender.AppenderFlag.REUSE_BUFFER)).asList(),
						DaemonThreadFactory.INSTANCE, bufferSize);
			}
		};
	}

	/**
	 * Creates.
	 * @param appenders appenders.
	 * @param threadFactory thread factory to create writer thread.
	 * @param bufferSize maximum queue elements.
	 * @return publisher.
	 */
	public static DisruptorLogPublisher of(Iterable<? extends LogAppender> appenders, ThreadFactory threadFactory,
			int bufferSize) {

		Disruptor<LogEventCell> disruptor = new Disruptor<>(LogEventCell::new, bufferSize, threadFactory,
				ProducerType.MULTI, new BlockingWaitStrategy());
		disruptor.setDefaultExceptionHandler(new LogExceptionHandler(disruptor::shutdown));

		boolean found = false;
		for (var appender : appenders) {
			disruptor.handleEventsWith(new LogEventHandler(appender));
			found = true;
		}
		if (!found) {
			throw new IllegalStateException();
		}
		var ringBuffer = disruptor.getRingBuffer();

		var router = new DisruptorLogPublisher(disruptor, ringBuffer);
		return router;
	}

	@Override
	public void start(LogConfig config) {
		disruptor.start();

	}

	DisruptorLogPublisher(Disruptor<LogEventCell> disruptor, RingBuffer<LogEventCell> ringBuffer) {
		super();
		this.disruptor = disruptor;
		this.ringBuffer = ringBuffer;
	}

	@Override
	public void log(LogEvent event) {
		long sequence = ringBuffer.next();
		try {
			LogEventCell cell = ringBuffer.get(sequence);
			cell.event = event;
		}
		finally {
			ringBuffer.publish(sequence);
		}

	}

	@Override
	public void close() {
		this.disruptor.halt();
	}

	private static class LogEventCell {

		@Nullable
		LogEvent event;

	}

	private static record LogEventHandler(LogAppender appender) implements EventHandler<LogEventCell> {

		@Override
		public void onEvent(LogEventCell event, long sequence, boolean endOfBatch) throws Exception {
			var logEvent = event.event;
			if (logEvent == null) {
				return;
			}
			appender.append(logEvent);
		}

	}

	private record LogExceptionHandler(Runnable shutdownHook) implements ExceptionHandler<Object> {

		@Override
		public void handleEventException(Throwable ex, long sequence, Object event) {
			if (ex instanceof InterruptedException) {
				shutdownHook.run();
			}
			else {
				MetaLog.error(DisruptorLogPublisher.class, ex);
				throw new RuntimeException(ex);
			}
		}

		@Override
		public void handleOnStartException(Throwable ex) {
			MetaLog.error(DisruptorLogPublisher.class, ex);
		}

		@Override
		public void handleOnShutdownException(Throwable ex) {
			MetaLog.error(DisruptorLogPublisher.class, ex);
		}

	}

}

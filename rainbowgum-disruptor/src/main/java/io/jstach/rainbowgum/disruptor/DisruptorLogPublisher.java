package io.jstach.rainbowgum.disruptor;

import java.util.Collection;
import java.util.List;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;

import org.jspecify.annotations.Nullable;

import com.lmax.disruptor.BlockingWaitStrategy;
import com.lmax.disruptor.EventHandler;
import com.lmax.disruptor.ExceptionHandler;
import com.lmax.disruptor.RingBuffer;
import com.lmax.disruptor.TimeoutException;
import com.lmax.disruptor.dsl.Disruptor;
import com.lmax.disruptor.dsl.ProducerType;
import com.lmax.disruptor.util.DaemonThreadFactory;

import io.jstach.rainbowgum.LogAlerts;
import io.jstach.rainbowgum.LogAppender;
import io.jstach.rainbowgum.LogConfig;
import io.jstach.rainbowgum.LogEvent;
import io.jstach.rainbowgum.LogPublisher;
import io.jstach.rainbowgum.LogPublisherRegistry;
import io.jstach.rainbowgum.LogPublisher.AsyncLogPublisher;
import io.jstach.rainbowgum.LogAppender.Appenders;

/**
 * Disruptor async publisher. Like the default async publisher it starts and closes its
 * appenders, and closing drains the events already published, waiting at most
 * {@value LogPublisherRegistry#ASYNC_SHUTDOWN_TIMEOUT} ms. Events logged after close are
 * dropped.
 */
public final class DisruptorLogPublisher implements AsyncLogPublisher {

	private final Disruptor<LogEventCell> disruptor;

	private final RingBuffer<LogEventCell> ringBuffer;

	private final List<? extends LogAppender> appenders;

	private final LogAlerts alerts;

	private volatile boolean closed;

	/**
	 * Creates a factory of disruptor log publishers.
	 * @param bufferSize ring buffer size.
	 * @return factory to generate this class.
	 */
	public static PublisherFactory of(int bufferSize) {
		return new PublisherFactory() {
			@Override
			public LogPublisher create(String name, LogConfig config, Appenders appenders) {
				return of(appenders.asList(), DaemonThreadFactory.INSTANCE, bufferSize, config.alerts());
			}
		};
	}

	/**
	 * Creates.
	 * @param appenders appenders.
	 * @param threadFactory thread factory to create writer thread.
	 * @param bufferSize maximum queue elements.
	 * @param alerts alerts for reporting internal errors (disruptor exception handler
	 * failures).
	 * @return publisher.
	 */
	public static DisruptorLogPublisher of(Collection<? extends LogAppender> appenders, ThreadFactory threadFactory,
			int bufferSize, LogAlerts alerts) {

		Disruptor<LogEventCell> disruptor = new Disruptor<>(LogEventCell::new, bufferSize, threadFactory,
				ProducerType.MULTI, new BlockingWaitStrategy());
		var router = new DisruptorLogPublisher(disruptor, disruptor.getRingBuffer(), List.copyOf(appenders), alerts);
		disruptor.setDefaultExceptionHandler(new LogExceptionHandler(router));

		boolean found = false;
		for (var appender : appenders) {
			disruptor.handleEventsWith(new LogEventHandler(appender));
			found = true;
		}
		if (!found) {
			throw new IllegalStateException();
		}

		return router;
	}

	@Override
	public void start(LogConfig config) {
		int started = 0;
		try {
			for (var appender : appenders) {
				appender.start(config);
				started++;
			}
			disruptor.start();
		}
		catch (RuntimeException | Error e) {
			closed = true;
			// try-with-resources rather than close(): see doc/checker-framework-bugs.md.
			for (var appender : appenders.subList(0, started)) {
				try (appender) {
				}
				catch (RuntimeException | Error closeFailure) {
					e.addSuppressed(closeFailure);
				}
			}
			throw e;
		}
	}

	private DisruptorLogPublisher(Disruptor<LogEventCell> disruptor, RingBuffer<LogEventCell> ringBuffer,
			List<? extends LogAppender> appenders, LogAlerts alerts) {
		super();
		this.disruptor = disruptor;
		this.ringBuffer = ringBuffer;
		this.appenders = appenders;
		this.alerts = alerts;
	}

	@Override
	public void log(LogEvent event) {
		if (closed) {
			// Nothing consumes the ring after close, so a full one would block forever.
			return;
		}
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
		if (closed) {
			return;
		}
		closed = true;
		try {
			disruptor.shutdown(LogPublisherRegistry.ASYNC_SHUTDOWN_TIMEOUT, TimeUnit.MILLISECONDS);
		}
		catch (TimeoutException e) {
			alerts.error(DisruptorLogPublisher.class, "Disruptor did not drain within "
					+ LogPublisherRegistry.ASYNC_SHUTDOWN_TIMEOUT + " ms; remaining events are dropped", e);
			disruptor.halt();
		}
		for (var appender : appenders) {
			try (appender) {
			}
			catch (RuntimeException e) {
				alerts.error(DisruptorLogPublisher.class, "Failed to close appender", e);
			}
		}
	}

	@Override
	public String toString() {
		return super.toString() + "[appenders=" + this.appenders + "]";
	}

	private static class LogEventCell {

		@Nullable LogEvent event;

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

	/*
	 * Rethrowing would stop the consumer thread, and every producer would then block once
	 * the ring is full, so a failed event is reported and the consumer carries on.
	 */
	private record LogExceptionHandler(DisruptorLogPublisher publisher) implements ExceptionHandler<Object> {

		@Override
		public void handleEventException(Throwable ex, long sequence, Object event) {
			publisher.alerts.error(DisruptorLogPublisher.class, ex);
		}

		@Override
		public void handleOnStartException(Throwable ex) {
			publisher.alerts.error(DisruptorLogPublisher.class, ex);
		}

		@Override
		public void handleOnShutdownException(Throwable ex) {
			publisher.alerts.error(DisruptorLogPublisher.class, ex);
		}

	}

}

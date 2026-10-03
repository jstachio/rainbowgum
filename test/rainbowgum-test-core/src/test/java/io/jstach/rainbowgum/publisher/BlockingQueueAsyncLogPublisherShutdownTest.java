package io.jstach.rainbowgum.publisher;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.util.ArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import io.jstach.rainbowgum.LogAppender;
import io.jstach.rainbowgum.LogConfig;
import io.jstach.rainbowgum.LogEvent;
import io.jstach.rainbowgum.TestLogEventFactory;
import io.jstach.rainbowgum.output.ListLogOutput;

class BlockingQueueAsyncLogPublisherShutdownTest {

	@ParameterizedTest
	@EnumSource(Shutdown.class)
	void shutdownCompletesProducersAlreadyBlockedOnTheFullQueue(Shutdown shutdown) throws Exception {
		for (int attempt = 0; attempt < 10; attempt++) {
			var entered = new CountDownLatch(1);
			var release = new CountDownLatch(1);
			var closed = new CountDownLatch(1);
			var output = new ListLogOutput() {
				@Override
				public void write(LogEvent event, String text) {
					entered.countDown();
					try {
						assertTrue(release.await(5, TimeUnit.SECONDS));
					}
					catch (InterruptedException e) {
						throw new AssertionError(e);
					}
					super.write(event, text);
				}

				@Override
				public void close() {
					super.close();
					closed.countDown();
				}
			};
			var config = LogConfig.builder().build();
			var appender = LogAppender.builder("test").output(output).build().provide("test", config);
			var publisher = BlockingQueueAsyncLogPublisher.of(appender, 1, config.alerts(),
					shutdown == Shutdown.TIMEOUT ? Duration.ofMillis(50) : Duration.ofSeconds(5));
			var producers = new ArrayList<Thread>();
			var closerInterrupted = new AtomicBoolean();
			var closer = new Thread(() -> {
				publisher.close();
				closerInterrupted.set(Thread.currentThread().isInterrupted());
			});
			publisher.start(config);
			try {
				publisher.log(TestLogEventFactory.of().event("first"));
				assertTrue(entered.await(5, TimeUnit.SECONDS));
				publisher.log(TestLogEventFactory.of().event("second"));
				int producerCount = shutdown == Shutdown.INTERRUPTED_PRODUCER ? 1 : 32;
				for (int i = 0; i < producerCount; i++) {
					var event = TestLogEventFactory.of().event("blocked " + i);
					var producer = new Thread(() -> publisher.log(event));
					producers.add(producer);
					producer.start();
					awaitWaiting(producer);
				}
				closer.start();
				if (shutdown != Shutdown.TIMEOUT) {
					awaitWaiting(closer);
				}
				if (shutdown == Shutdown.INTERRUPTED_PRODUCER) {
					producers.get(0).interrupt();
					producers.get(0).join(5000);
					assertFalse(producers.get(0).isAlive());
				}
				if (shutdown == Shutdown.INTERRUPTED_CLOSER) {
					closer.interrupt();
				}
				if (shutdown == Shutdown.TIMEOUT || shutdown == Shutdown.INTERRUPTED_CLOSER) {
					closer.join(5000);
					assertFalse(closer.isAlive());
					assertEquals(1, closed.getCount(), "output must remain open while the write is paused");
				}
				release.countDown();
				closer.join(10000);
				assertFalse(closer.isAlive());
				assertEquals(shutdown == Shutdown.INTERRUPTED_CLOSER, closerInterrupted.get());
				assertTrue(closed.await(5, TimeUnit.SECONDS));
				long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
				for (var producer : producers) {
					long remaining = deadline - System.nanoTime();
					if (remaining > 0) {
						producer.join(Math.max(1, TimeUnit.NANOSECONDS.toMillis(remaining)));
					}
				}
				assertEquals(0, producers.stream().filter(Thread::isAlive).count(),
						"close returned with admitted producers still blocked; wrote " + output.events().size()
								+ " of 34 events; attempt " + attempt);
				assertEquals(shutdown == Shutdown.INTERRUPTED_PRODUCER ? 2 : 34, output.events().size(),
						"all admitted events must be written; attempt " + attempt);
			}
			finally {
				release.countDown();
				for (var producer : producers) {
					producer.interrupt();
					producer.join(1000);
				}
				publisher.close();
				closer.join(1000);
			}
		}
	}

	private enum Shutdown {

		NORMAL, TIMEOUT, INTERRUPTED_PRODUCER, INTERRUPTED_CLOSER

	}

	private static void awaitWaiting(Thread thread) throws InterruptedException {
		long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
		while (thread.getState() != Thread.State.WAITING && thread.getState() != Thread.State.TIMED_WAITING) {
			assertTrue(thread.isAlive());
			assertTrue(System.nanoTime() < deadline);
			Thread.sleep(1);
		}
	}

}

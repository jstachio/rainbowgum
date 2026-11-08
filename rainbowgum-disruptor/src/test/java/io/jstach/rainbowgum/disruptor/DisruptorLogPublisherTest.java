package io.jstach.rainbowgum.disruptor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.System.Logger.Level;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;

import com.lmax.disruptor.util.DaemonThreadFactory;

import io.jstach.rainbowgum.KeyValues;
import io.jstach.rainbowgum.LogAppender;
import io.jstach.rainbowgum.LogConfig;
import io.jstach.rainbowgum.LogEncoder;
import io.jstach.rainbowgum.LogEvent;
import io.jstach.rainbowgum.LogFormatter;
import io.jstach.rainbowgum.output.ListLogOutput;

class DisruptorLogPublisherTest {

	private static final int BUFFER_SIZE = 8;

	@Test
	void closeReleasesAProducerWaitingOnAFullRing() throws InterruptedException {
		var config = LogConfig.builder().build();
		var output = new ClosingListLogOutput();
		var writing = new CountDownLatch(1);
		var releaseOutput = new CountDownLatch(1);
		var producerEntered = new CountDownLatch(1);
		var producerDone = new CountDownLatch(1);
		output.setConsumer((e, s) -> {
			if (e.message().equals("held")) {
				writing.countDown();
				try {
					releaseOutput.await();
				}
				catch (InterruptedException ex) {
					Thread.currentThread().interrupt();
					throw new IllegalStateException(ex);
				}
			}
		});
		try (var publisher = publisher(config, output)) {
			var producer = Thread.ofPlatform().daemon().unstarted(() -> {
				producerEntered.countDown();
				publisher.log(event("waiting"));
				producerDone.countDown();
			});
			var closer = Thread.ofPlatform().daemon().unstarted(publisher::close);
			try {
				publisher.start(config);
				publisher.log(event("held"));
				assertTrue(writing.await(5, TimeUnit.SECONDS));
				for (int i = 1; i < BUFFER_SIZE; i++) {
					publisher.log(event("queued" + i));
				}
				producer.start();
				assertTrue(producerEntered.await(5, TimeUnit.SECONDS));
				assertFalse(producerDone.await(50, TimeUnit.MILLISECONDS));
				closer.start();
				// The output is still blocked. Shutdown must release the producer
				// without depending on the consumer making space first.
				assertTrue(producerDone.await(5, TimeUnit.SECONDS));
			}
			finally {
				releaseOutput.countDown();
				producer.join(5000);
				closer.join(5000);
			}
			assertFalse(producer.isAlive());
			assertFalse(closer.isAlive());
		}
		assertEquals(BUFFER_SIZE, output.events().size());
		assertEquals(1, output.closes);
	}

	@Test
	void startingTwiceDoesNotCloseRunningAppenders() {
		var config = LogConfig.builder().build();
		var output = new ClosingListLogOutput();
		try (var publisher = publisher(config, output)) {
			publisher.start(config);
			assertDoesNotThrow(() -> publisher.start(config));
			publisher.log(event("still running"));
		}
		assertEquals(1, output.starts);
		assertEquals(1, output.closes);
		assertEquals(List.of("still running"), output.events().stream().map(e -> e.getValue()).toList());
	}

	@Test
	void closingBeforeStartPreventsRestartAndClosesOnlyOnce() {
		var config = LogConfig.builder().build();
		var output = new ClosingListLogOutput();
		try (var publisher = publisher(config, output)) {
			publisher.close();
			var error = assertThrows(IllegalStateException.class, () -> publisher.start(config));
			assertEquals("DisruptorLogPublisher cannot be restarted.", error.getMessage());
		}
		assertEquals(0, output.starts);
		assertEquals(1, output.closes);
	}

	@Test
	void failedAppenderStartClosesItsPartiallyStartedOutput() {
		var config = LogConfig.builder().build();
		var output = new ClosingListLogOutput() {
			@Override
			public void start(LogConfig config) {
				super.start(config);
				throw new IllegalStateException("start failed");
			}
		};
		try (var publisher = publisher(config, output)) {
			var error = assertThrows(IllegalStateException.class, () -> publisher.start(config));
			assertEquals("start failed", error.getMessage());
		}
		assertEquals(1, output.starts);
		assertEquals(1, output.closes);
	}

	@Test
	void closeDrainsPublishedEventsAndClosesAppenders() {
		var config = LogConfig.builder().build();
		var output = new ClosingListLogOutput();
		var publisher = publisher(config, output);
		publisher.start(config);
		for (int i = 0; i < 100; i++) {
			publisher.log(event("event" + i));
		}
		publisher.close();
		assertEquals(100, output.events().size());
		assertTrue(output.closed);
	}

	@Test
	void closeRightAfterStartWritesEventsBeforeTheConsumerRan() {
		var config = LogConfig.builder().build();
		var output = new ClosingListLogOutput();
		// Disruptor's shutdown skips a consumer whose thread has not run yet.
		ThreadFactory slowThreads = r -> DaemonThreadFactory.INSTANCE.newThread(() -> {
			try {
				Thread.sleep(200);
			}
			catch (InterruptedException e) {
				Thread.currentThread().interrupt();
			}
			r.run();
		});
		var publisher = DisruptorLogPublisher.of(List.of(appender(config, output)), slowThreads, BUFFER_SIZE,
				config.alerts());
		publisher.start(config);
		publisher.log(event("early"));
		publisher.close();
		assertEquals(List.of("early"), output.events().stream().map(e -> e.getValue()).toList());
	}

	@Test
	void logAfterCloseDoesNotBlock() {
		var config = LogConfig.builder().build();
		var output = new ClosingListLogOutput();
		var publisher = publisher(config, output);
		publisher.start(config);
		publisher.close();
		assertTimeoutPreemptively(Duration.ofSeconds(5), () -> {
			for (int i = 0; i < BUFFER_SIZE * 4; i++) {
				publisher.log(event("late" + i));
			}
		});
		assertEquals(0, output.events().size());
	}

	@Test
	void errorFromAnOutputDoesNotStopTheConsumer() {
		var config = LogConfig.builder().build();
		var output = new ClosingListLogOutput();
		output.setConsumer((e, s) -> {
			if (e.message().equals("boom")) {
				throw new Error("boom");
			}
		});
		var publisher = publisher(config, output);
		publisher.start(config);
		assertTimeoutPreemptively(Duration.ofSeconds(5), () -> {
			publisher.log(event("boom"));
			for (int i = 0; i < BUFFER_SIZE * 4; i++) {
				publisher.log(event("after" + i));
			}
			publisher.close();
		});
		assertEquals(BUFFER_SIZE * 4,
				output.events().stream().filter(e -> e.getKey().message().startsWith("after")).count());
	}

	private static DisruptorLogPublisher publisher(LogConfig config, ListLogOutput output) {
		return DisruptorLogPublisher.of(List.of(appender(config, output)), DaemonThreadFactory.INSTANCE, BUFFER_SIZE,
				config.alerts());
	}

	private static LogAppender appender(LogConfig config, ListLogOutput output) {
		LogEncoder encoder = LogFormatter.builder().message().encoder().build().provide("test", config);
		return LogAppender.builder("test").output(output).encoder(encoder).build().provide("test", config);
	}

	private static LogEvent event(String message) {
		return LogEvent.of(Instant.EPOCH, "main", 1, Level.INFO, "test", message, KeyValues.of(), null);
	}

	static class ClosingListLogOutput extends ListLogOutput {

		volatile boolean closed;

		int starts;

		int closes;

		@Override
		public void start(LogConfig config) {
			starts++;
		}

		@Override
		public void close() {
			// ListLogOutput.close() does nothing, and calling it crashes the Checker
			// Framework (doc/checker-framework-bugs.md).
			closed = true;
			closes++;
		}

	}

}

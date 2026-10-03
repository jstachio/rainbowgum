package io.jstach.rainbowgum;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.Test;

import io.jstach.rainbowgum.output.ListLogOutput;
import io.jstach.rainbowgum.publisher.OpusAsyncPublisher;

/*
 * Uses real threads, so it lives in this module rather than core.
 */
class OpusAsyncPublisherTest {

	static final LogFormatter FORMAT = LogFormatter.builder().message().newline().build();

	static LogPublisher publisher(LogConfig config, LogOutput output, int bufferSize, Duration timeout) {
		LogProvider<LogAppender> appender = LogAppender.builder("a").output(output).formatter(FORMAT).build();
		var appenders = new LogAppender.Appenders("test", config, List.of(appender));
		return OpusAsyncPublisher.builder()
			.bufferSize(bufferSize)
			.shutdownTimeout(timeout)
			.build()
			.create("test", config, appenders);
	}

	static LogEvent event(String message) {
		return TestLogEventFactory.of().event(message);
	}

	static List<String> messages(ListLogOutput output) {
		return output.events().stream().map(e -> e.getValue().trim()).toList();
	}

	static long dropped(LogConfig config) {
		return metric(config, LogMetrics.EVENTS_DROPPED_METRIC);
	}

	static long metric(LogConfig config, String name) {
		return config.metrics()
			.snapshot()
			.stream()
			.filter(c -> c.name().equals(name))
			.mapToLong(LogMetrics.Metric::value)
			.sum();
	}

	static List<String> errorAlerts(LogConfig config) {
		return config.alerts()
			.dump()
			.stream()
			.filter(e -> e.level() == System.Logger.Level.ERROR)
			.map(e -> e.throwableOrNull() == null ? e.message() : String.valueOf(e.throwableOrNull()))
			.toList();
	}

	/*
	 * Blocks every write until released, and signals when the first write starts.
	 */
	static final class BlockingOutput extends ListLogOutput {

		final CountDownLatch writing = new CountDownLatch(1);

		final CountDownLatch release = new CountDownLatch(1);

		@Override
		public void write(LogEvent event, String s) {
			writing.countDown();
			try {
				release.await(30, TimeUnit.SECONDS);
			}
			catch (InterruptedException e) {
				Thread.currentThread().interrupt();
			}
			super.write(event, s);
		}

	}

	@Test
	void manyProducersDeliverEverythingInPerThreadOrder() throws Exception {
		var config = LogConfig.builder().build();
		var output = new ListLogOutput();
		var pub = publisher(config, output, 16, Duration.ofSeconds(10));
		pub.start(config);
		int threads = 8;
		int perThread = 5000;
		var start = new CountDownLatch(1);
		var producers = new ArrayList<Thread>();
		for (int t = 0; t < threads; t++) {
			int id = t;
			producers.add(Thread.ofPlatform().start(() -> {
				try {
					start.await();
				}
				catch (InterruptedException e) {
					throw new RuntimeException(e);
				}
				for (int i = 0; i < perThread; i++) {
					pub.log(event(id + ":" + i));
				}
			}));
		}
		start.countDown();
		for (var p : producers) {
			p.join();
		}
		pub.close();
		var lines = messages(output);
		assertEquals(threads * perThread, lines.size());
		int[] next = new int[threads];
		for (var line : lines) {
			var parts = line.split(":");
			int id = Integer.parseInt(parts[0]);
			assertEquals(next[id]++, Integer.parseInt(parts[1]), () -> "out of order for producer " + id);
		}
		assertEquals(0, dropped(config));
	}

	@Test
	void closeWritesEverythingAcceptedAndNeverInterruptsTheOutput() {
		var interrupted = new AtomicInteger();
		var closedInterrupted = new AtomicBoolean();
		var output = new ListLogOutput() {
			@Override
			public void write(LogEvent event, String s) {
				if (Thread.currentThread().isInterrupted()) {
					interrupted.incrementAndGet();
				}
				try {
					Thread.sleep(1);
				}
				catch (InterruptedException e) {
					interrupted.incrementAndGet();
					Thread.currentThread().interrupt();
				}
				super.write(event, s);
			}

			@Override
			public void close() {
				closedInterrupted.set(Thread.currentThread().isInterrupted());
				super.close();
			}
		};
		var config = LogConfig.builder().build();
		var pub = publisher(config, output, 4, Duration.ofSeconds(30));
		pub.start(config);
		for (int i = 0; i < 200; i++) {
			pub.log(event("" + i));
		}
		pub.close();
		assertEquals(java.util.stream.IntStream.range(0, 200).mapToObj(String::valueOf).toList(), messages(output));
		assertEquals(0, interrupted.get());
		assertFalse(closedInterrupted.get());
	}

	@Test
	void closeThatTimesOutReportsQueuedEvents() throws Exception {
		var output = new BlockingOutput();
		var config = LogConfig.builder().build();
		var pub = publisher(config, output, 10, Duration.ofMillis(100));
		pub.start(config);
		try {
			pub.log(event("first"));
			assertTrue(output.writing.await(5, TimeUnit.SECONDS));
			pub.log(event("second"));
			pub.log(event("third"));
			pub.close();
			assertEquals(List.of("java.util.concurrent.TimeoutException: Async publisher 'test' did not finish within "
					+ "100ms of close; 2 events still queued"), errorAlerts(config));
		}
		finally {
			output.release.countDown();
		}
	}

	@Test
	void eventsQueuedGaugeTracksWaitingEvents() throws Exception {
		var output = new BlockingOutput();
		var config = LogConfig.builder().build();
		var pub = publisher(config, output, 10, Duration.ofSeconds(10));
		pub.log(event("before start"));
		pub.start(config);
		assertTrue(output.writing.await(5, TimeUnit.SECONDS));
		pub.log(event("second"));
		pub.log(event("third"));
		assertEquals(2, metric(config, LogMetrics.EVENTS_QUEUED_METRIC));
		output.release.countDown();
		pub.close();
		assertEquals(0, metric(config, LogMetrics.EVENTS_QUEUED_METRIC));
		assertEquals(List.of("before start", "second", "third"), messages(output));
	}

	@Test
	void logAfterCloseIsDroppedAndCountedNotThrown() {
		var config = LogConfig.builder().build();
		var output = new ListLogOutput();
		var pub = publisher(config, output, 10, Duration.ofSeconds(10));
		pub.start(config);
		pub.close();
		pub.log(event("a"));
		pub.log(event("b"));
		pub.log(event("c"));
		assertEquals(3, dropped(config));
		assertEquals(
				List.of("java.lang.IllegalStateException: Async publisher 'test' dropped 1 event(s): the publisher "
						+ "is closed. Further drops for this reason are only counted in events.dropped"),
				errorAlerts(config));
		assertEquals(List.of(), messages(output));
	}

	@Test
	void producerWaitingOnAFullQueueIsReleasedByClose() throws Exception {
		var output = new BlockingOutput();
		var config = LogConfig.builder().build();
		var pub = publisher(config, output, 1, Duration.ofMillis(100));
		pub.start(config);
		try {
			pub.log(event("taken"));
			assertTrue(output.writing.await(5, TimeUnit.SECONDS));
			pub.log(event("queued"));
			var waiting = Thread.ofPlatform().start(() -> pub.log(event("waiting")));
			long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
			while (waiting.getState() != Thread.State.WAITING && System.nanoTime() < deadline) {
				Thread.sleep(1);
			}
			assertEquals(Thread.State.WAITING, waiting.getState());
			pub.close();
			waiting.join(5_000);
			assertFalse(waiting.isAlive(), "a producer waiting for space must be released by close");
			assertEquals(1, dropped(config));
		}
		finally {
			output.release.countDown();
		}
	}

	@Test
	void interruptedProducerDropsTheEventAndKeepsItsInterrupt() throws Exception {
		var output = new BlockingOutput();
		var config = LogConfig.builder().build();
		var pub = publisher(config, output, 1, Duration.ofMillis(100));
		pub.start(config);
		try {
			pub.log(event("taken"));
			assertTrue(output.writing.await(5, TimeUnit.SECONDS));
			pub.log(event("queued"));
			var stillInterrupted = new AtomicBoolean();
			var t = Thread.ofPlatform().start(() -> {
				Thread.currentThread().interrupt();
				pub.log(event("interrupted"));
				stillInterrupted.set(Thread.currentThread().isInterrupted());
			});
			t.join(5_000);
			assertTrue(stillInterrupted.get());
			assertEquals(1, dropped(config));
			assertEquals(List.of("java.lang.IllegalStateException: Async publisher 'test' dropped 1 event(s): the "
					+ "logging thread was interrupted while the queue was full. Further drops for this reason are only "
					+ "counted in events.dropped"), errorAlerts(config));
		}
		finally {
			output.release.countDown();
			pub.close();
		}
	}

	@Test
	void consumerLoggingIntoAFullQueueDoesNotDeadlock() throws Exception {
		var pubRef = new AtomicReference<LogPublisher>();
		var output = new ListLogOutput() {
			@Override
			public void write(LogEvent event, String s) {
				super.write(event, s);
				if (s.trim().equals("trigger")) {
					for (int i = 0; i < 5; i++) {
						pubRef.get().log(event("from consumer " + i));
					}
				}
			}
		};
		var config = LogConfig.builder().build();
		var pub = publisher(config, output, 1, Duration.ofSeconds(10));
		pubRef.set(pub);
		pub.start(config);
		pub.log(event("trigger"));
		// Wait for the reentrant event to be written; closing earlier would drop it as
		// closed.
		long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
		while (output.events().size() < 2 && System.nanoTime() < deadline) {
			Thread.sleep(1);
		}
		var closer = Thread.ofPlatform().start(pub::close);
		closer.join(10_000);
		assertFalse(closer.isAlive(), "close must not hang");
		assertEquals(List.of("trigger", "from consumer 0"), messages(output));
		assertEquals(4, dropped(config));
	}

	@Test
	void anErrorFromTheAppenderDoesNotStopTheConsumer() {
		var output = new ListLogOutput() {
			@Override
			public void write(LogEvent event, String s) {
				if (s.trim().equals("boom")) {
					throw new AssertionError("boom");
				}
				super.write(event, s);
			}
		};
		var config = LogConfig.builder().build();
		var pub = publisher(config, output, 1, Duration.ofSeconds(10));
		pub.start(config);
		pub.log(event("boom"));
		pub.log(event("after"));
		pub.close();
		assertEquals(List.of("after"), messages(output));
		var alert = config.alerts().dump().get(config.alerts().dump().size() - 1);
		assertEquals("Async publisher 'test' appender failed on a batch of 1 events", alert.message());
		assertEquals("java.lang.AssertionError: boom", String.valueOf(alert.throwableOrNull()));
	}

	@Test
	void eventsLoggedBeforeStartAreDeliveredAndOverflowIsDropped() {
		var config = LogConfig.builder().build();
		var output = new ListLogOutput();
		var pub = publisher(config, output, 2, Duration.ofSeconds(10));
		pub.log(event("one"));
		pub.log(event("two"));
		pub.log(event("three"));
		pub.start(config);
		pub.close();
		assertEquals(List.of("one", "two"), messages(output));
		assertEquals(1, dropped(config));
		assertEquals(List.of("java.lang.IllegalStateException: Async publisher 'test' dropped 1 event(s): the queue is "
				+ "full and the publisher has not been started. Further drops for this reason are only counted in "
				+ "events.dropped"), errorAlerts(config));
	}

	@Test
	void lifecycleMisuse() {
		var config = LogConfig.builder().build();
		var output = new ListLogOutput();
		var neverStarted = publisher(config, output, 2, Duration.ofSeconds(10));
		neverStarted.log(event("lost"));
		neverStarted.close();
		neverStarted.close();
		assertEquals(1, dropped(config));

		var pub = publisher(config, new ListLogOutput(), 2, Duration.ofSeconds(10));
		pub.start(config);
		assertEquals("OpusAsyncPublisher 'test' can only be started once",
				assertThrows(IllegalStateException.class, () -> pub.start(config)).getMessage());
		pub.close();
		pub.close();
	}

	@Test
	void worksAsARoutePublisher() {
		var output = new ListLogOutput();
		var gum = RainbowGum.builder(LogConfig.builder().build()).route(r -> {
			r.appender("list", a -> a.output(output).formatter(FORMAT));
			r.publisher(OpusAsyncPublisher.builder().bufferSize(8).build());
		}).build();
		try (var g = gum.start()) {
			for (int i = 0; i < 100; i++) {
				g.log(event("" + i));
			}
		}
		assertEquals(java.util.stream.IntStream.range(0, 100).mapToObj(String::valueOf).toList(), messages(output));
	}

}

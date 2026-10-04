package io.jstach.rainbowgum;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.System.Logger.Level;
import java.net.URI;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import io.jstach.rainbowgum.output.ListLogOutput;

@Timeout(30)
class BatchSwapAsyncLogPublisherTest {

	enum Producers {

		PLATFORM, VIRTUAL;

		Thread start(Runnable task) {
			return this == VIRTUAL ? Thread.ofVirtual().start(task) : Thread.ofPlatform().start(task);
		}

	}

	@ParameterizedTest
	@EnumSource
	void deliversEveryEventInProducerOrder(Producers kind) throws Exception {
		for (int capacity : new int[] { 1, 17, 1024 }) {
			var output = new LifecycleOutput();
			var config = LogConfig.builder().build();
			var publisher = publisher(config, output, capacity, Duration.ofSeconds(5));
			publisher.start(config);
			var start = new CountDownLatch(1);
			var failure = new AtomicReference<Throwable>();
			var threads = new ArrayList<Thread>();
			try {
				for (int p = 0; p < 8; p++) {
					int producer = p;
					threads.add(kind.start(() -> {
						try {
							await(start);
							for (int i = 0; i < 2000; i++) {
								publisher.log(event(producer + ":" + i));
							}
						}
						catch (Throwable e) {
							failure.compareAndSet(null, e);
						}
					}));
				}
				start.countDown();
				for (var thread : threads) {
					join(thread);
				}
			}
			finally {
				publisher.close();
			}
			assertEquals(null, failure.get());
			assertEquals(16000, output.events().size());
			int[] next = new int[8];
			for (var entry : output.events()) {
				String[] parts = entry.getKey().message().split(":");
				int p = Integer.parseInt(parts[0]);
				assertEquals(next[p]++, Integer.parseInt(parts[1]));
			}
			assertEquals(0, queued(config));
			assertEquals(1, output.starts.get());
			assertEquals(1, output.closes.get());
			assertEquals(List.of(), config.alerts().dump());
		}
	}

	enum Shutdown {

		NORMAL, TIMEOUT, INTERRUPTED_PRODUCER, INTERRUPTED_CLOSER, PRE_INTERRUPTED_CLOSER

	}

	@ParameterizedTest
	@EnumSource
	void shutdownDrainsAdmittedProducers(Shutdown mode) throws Exception {
		for (int repeat = 0; repeat < 3; repeat++) {
			var entered = new CountDownLatch(1);
			var release = new CountDownLatch(1);
			var output = new LifecycleOutput();
			output.setConsumer((event, text) -> {
				if (event.message().equals("first")) {
					entered.countDown();
					await(release);
				}
			});
			var config = LogConfig.builder().build();
			var publisher = publisher(config, output, 1,
					mode == Shutdown.TIMEOUT ? Duration.ofMillis(50) : Duration.ofSeconds(5));
			publisher.start(config);
			var failure = new AtomicReference<Throwable>();
			var producerInterrupted = new AtomicBoolean();
			var closerInterrupted = new AtomicBoolean();
			var threads = new ArrayList<Thread>();
			try {
				publisher.log(event("first"));
				await(entered);
				publisher.log(event("second"));
				assertEquals(1, queued(config));
				for (int p = 0; p < 16; p++) {
					String message = "blocked" + p;
					var thread = Thread.ofVirtual().start(() -> {
						try {
							publisher.log(event(message));
							if (Thread.currentThread().isInterrupted()) {
								producerInterrupted.set(true);
							}
						}
						catch (Throwable e) {
							failure.compareAndSet(null, e);
						}
					});
					threads.add(thread);
					awaitState(thread, Thread.State.WAITING);
				}
				if (mode == Shutdown.INTERRUPTED_PRODUCER) {
					threads.getFirst().interrupt();
					join(threads.getFirst());
					assertTrue(producerInterrupted.get());
					assertEquals(
							"""
									Async publisher 'test' dropped 1 event(s): the logging thread was interrupted while the buffer was full. Further drops for this reason are only counted in events.dropped.\
									""",
							config.alerts().dump().getFirst().message());
				}
				var closer = Thread.ofPlatform().start(() -> {
					if (mode == Shutdown.PRE_INTERRUPTED_CLOSER) {
						Thread.currentThread().interrupt();
					}
					publisher.close();
					closerInterrupted.set(Thread.currentThread().isInterrupted());
				});
				if (mode == Shutdown.TIMEOUT) {
					join(closer);
				}
				else {
					awaitState(closer, Thread.State.TIMED_WAITING);
				}
				if (mode == Shutdown.INTERRUPTED_CLOSER) {
					closer.interrupt();
					join(closer);
				}
				assertEquals(0, output.closes.get());
				publisher.log(event("late"));
				assertEquals(
						"""
								Async publisher 'test' dropped 1 event(s): the publisher is closing or closed. Further drops for this reason are only counted in events.dropped.\
								""",
						config.alerts().dump().getLast().message());
				release.countDown();
				for (var thread : threads) {
					join(thread);
				}
				join(closer);
				publisher.close();
				assertEquals(mode == Shutdown.INTERRUPTED_CLOSER || mode == Shutdown.PRE_INTERRUPTED_CLOSER,
						closerInterrupted.get());
				assertEquals(null, failure.get());
				assertEquals(mode == Shutdown.INTERRUPTED_PRODUCER ? 17 : 18, output.events().size());
				assertEquals(mode == Shutdown.INTERRUPTED_PRODUCER ? 2 : 1,
						metric(config, LogMetrics.EVENTS_DROPPED_METRIC));
				assertEquals(0, queued(config));
				assertEquals(1, output.closes.get());
			}
			finally {
				release.countDown();
				publisher.close();
			}
		}
	}

	@Test
	void workerFailureReleasesBlockedProducers() throws Exception {
		var output = new LifecycleOutput();
		var entered = new CountDownLatch(1);
		var release = new CountDownLatch(1);
		output.setConsumer((event, text) -> {
			entered.countDown();
			await(release);
			throw new AssertionError("fatal output failure");
		});
		var config = LogConfig.builder().build();
		var publisher = publisher(config, output, 1, Duration.ofSeconds(5));
		publisher.start(config);
		var listenerCompleted = new AtomicBoolean();
		try (var registration = config.alerts().addListener(alert -> {
			if (alert.message().equals("Async worker failed.")) {
				var thread = Thread.ofVirtual().start(() -> publisher.log(event("from alert listener")));
				try {
					join(thread);
					listenerCompleted.set(true);
				}
				catch (InterruptedException e) {
					Thread.currentThread().interrupt();
					throw new AssertionError(e);
				}
			}
		})) {
			publisher.log(event("first"));
			await(entered);
			publisher.log(event("pending"));
			var failure = new AtomicReference<Throwable>();
			var producer = Thread.ofPlatform().start(() -> {
				try {
					publisher.log(event("blocked"));
				}
				catch (Throwable e) {
					failure.set(e);
				}
			});
			awaitState(producer, Thread.State.WAITING);
			release.countDown();
			join(producer);
			publisher.close();
			assertEquals(null, failure.get());
			assertEquals(
					"""
							Async publisher 'test' dropped 1 event(s): the worker stopped before delivering the event. Further drops for this reason are only counted in events.dropped.
							Async worker failed.
							""",
					config.alerts()
						.dump()
						.stream()
						.map(LogEvent::message)
						.sorted()
						.collect(java.util.stream.Collectors.joining("\n", "", "\n")));
			assertTrue(listenerCompleted.get());
			assertEquals(3, metric(config, LogMetrics.EVENTS_DROPPED_METRIC));
			assertEquals(1, metric(config, LogMetrics.EVENTS_FAILED_METRIC));
			assertEquals(0, queued(config));
			assertEquals(1, output.closes.get());
		}
		finally {
			release.countDown();
			publisher.close();
		}
	}

	@Test
	void startsAndClosesAppenderExactlyOnce() {
		var output = new LifecycleOutput();
		var config = LogConfig.builder().build();
		var publisher = publisher(config, output, 1, Duration.ofSeconds(5));
		publisher.log(event("early"));
		publisher.log(event("early again"));
		assertEquals(
				"""
						Async publisher 'test' dropped 1 event(s): the publisher has not been started. Further drops for this reason are only counted in events.dropped.\
						""",
				config.alerts().dump().getFirst().message());
		assertEquals(1, config.alerts().dump().size());
		assertEquals(2, metric(config, LogMetrics.EVENTS_DROPPED_METRIC));
		publisher.start(config);
		publisher.start(config);
		publisher.log(event("one"));
		publisher.close();
		publisher.close();
		assertEquals(1, output.starts.get());
		assertEquals(1, output.closes.get());
		assertEquals(1, output.events().size());
		assertEquals("""
				BatchSwapAsyncLogPublisher cannot be restarted.\
				""", assertThrows(IllegalStateException.class, () -> publisher.start(config)).getMessage());
	}

	@Test
	void closesWithoutStarting() {
		var output = new LifecycleOutput();
		var config = LogConfig.builder().build();
		var publisher = publisher(config, output, 1, Duration.ZERO);
		publisher.close();
		publisher.close();
		assertEquals(0, output.starts.get());
		assertEquals(1, output.closes.get());
	}

	@Test
	void startupFailureClosesResources() {
		var output = new LifecycleOutput();
		output.failStart = true;
		var config = LogConfig.builder().build();
		var publisher = publisher(config, output, 1, Duration.ofSeconds(5));
		assertEquals("""
				startup failed\
				""", assertThrows(IllegalStateException.class, () -> publisher.start(config)).getMessage());
		publisher.close();
		assertEquals(1, output.closes.get());
	}

	@Test
	void rejectsRecursiveLoggingAndAllowsWorkerToRequestClose() {
		var output = new LifecycleOutput();
		var config = LogConfig.builder().build();
		var publisher = publisher(config, output, 1, Duration.ofSeconds(5));
		output.setConsumer((event, text) -> {
			publisher.log(event("recursive"));
			publisher.log(event("recursive again"));
			publisher.close();
		});
		publisher.start(config);
		publisher.log(event("one"));
		publisher.close();
		assertEquals(
				"""
						Async publisher 'test' dropped 1 event(s): the publisher's own thread logged recursively. Further drops for this reason are only counted in events.dropped.\
						""",
				config.alerts().dump().getFirst().message());
		assertEquals(1, config.alerts().dump().size());
		assertEquals(2, metric(config, LogMetrics.EVENTS_DROPPED_METRIC));
		assertEquals(1, output.events().size());
		assertEquals(1, output.closes.get());
	}

	@Test
	void concurrentDropsAreCountedButAlertedOnce() throws Exception {
		var config = LogConfig.builder().build();
		var publisher = publisher(config, new LifecycleOutput(), 1, Duration.ofSeconds(5));
		publisher.close();
		var start = new CountDownLatch(1);
		var threads = new ArrayList<Thread>();
		for (int i = 0; i < 16; i++) {
			threads.add(Thread.ofVirtual().start(() -> {
				await(start);
				for (int n = 0; n < 100; n++) {
					publisher.log(event("closed"));
				}
			}));
		}
		start.countDown();
		for (var thread : threads) {
			join(thread);
		}
		assertEquals(1600, metric(config, LogMetrics.EVENTS_DROPPED_METRIC));
		assertEquals(1, config.alerts().dump().size());
		assertEquals(
				"""
						Async publisher 'test' dropped 1 event(s): the publisher is closing or closed. Further drops for this reason are only counted in events.dropped.\
						""",
				config.alerts().dump().getFirst().message());
	}

	@Test
	void interruptedCallerEnqueuesWhenSpaceIsAvailable() throws Exception {
		var output = new LifecycleOutput();
		var config = LogConfig.builder().build();
		var publisher = publisher(config, output, 1, Duration.ofSeconds(5));
		publisher.start(config);
		var interrupted = new AtomicBoolean();
		var producer = Thread.ofPlatform().start(() -> {
			Thread.currentThread().interrupt();
			publisher.log(event("interrupted"));
			interrupted.set(Thread.currentThread().isInterrupted());
		});
		join(producer);
		publisher.close();
		assertTrue(interrupted.get());
		assertEquals(1, output.events().size());
		assertEquals(0, metric(config, LogMetrics.EVENTS_DROPPED_METRIC));
		assertEquals(List.of(), config.alerts().dump());
	}

	enum Configuration {

		ASYNC, CORE_ASYNC, URI, PROPERTIES_OVERRIDE, BUILDER, GENERATED_BUILDER

	}

	@ParameterizedTest
	@EnumSource
	void defaultPublisherHonorsConfiguration(Configuration mode) throws Exception {
		var output = new LifecycleOutput();
		var entered = new CountDownLatch(1);
		var release = new CountDownLatch(1);
		output.setConsumer((event, text) -> {
			if (event.message().equals("first")) {
				entered.countDown();
				await(release);
			}
		});
		// Named properties take precedence over URI defaults.
		var properties = LogProperties.builder().fromProperties(mode == Configuration.URI ? """
				""" : """
				logging.publisher.test.bufferSize=1
				logging.publisher.test.shutdownTimeout=0
				""").build();
		var config = LogConfig.builder().properties(properties).build();
		LogPublisher.PublisherFactory factory = switch (mode) {
			case ASYNC -> config.publisherRegistry().provide(LogProviderRef.of(URI.create("async")));
			case CORE_ASYNC -> config.publisherRegistry().provide(LogProviderRef.of(URI.create("core.async")));
			case URI -> config.publisherRegistry()
				.provide(LogProviderRef.of(URI.create("async:///?bufferSize=1&shutdownTimeout=0")));
			case PROPERTIES_OVERRIDE -> config.publisherRegistry()
				.provide(LogProviderRef.of(URI.create("async:///?bufferSize=17&shutdownTimeout=10000")));
			case GENERATED_BUILDER -> BatchSwapAsyncLogPublisher.builder("test")
				.bufferSize(17)
				.shutdownTimeout(Duration.ofSeconds(10))
				.fromProperties(config.properties())
				.build();
			case BUILDER -> LogPublisher.AsyncLogPublisher.builder().bufferSize(1).build();
		};
		var appender = LogAppender.builder("test")
			.output(output)
			.formatter(LogFormatter.builder().message().build())
			.build();
		var publisher = factory.create("test", config, new LogAppender.Appenders("test", config, List.of(appender)));
		assertEquals(BatchSwapAsyncLogPublisher.class, publisher.getClass());
		publisher.start(config);
		try {
			publisher.log(event("first"));
			await(entered);
			publisher.log(event("second"));
			var producer = Thread.ofPlatform().start(() -> publisher.log(event("third")));
			awaitState(producer, Thread.State.WAITING);
			assertEquals(1, queued(config));
			publisher.close();
			assertEquals("""
					Async publisher is still draining after close.\
					""", config.alerts().dump().getFirst().message());
			assertEquals(0, output.closes.get());
			release.countDown();
			join(producer);
			long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
			while (output.closes.get() == 0 && System.nanoTime() < deadline) {
				Thread.sleep(1);
			}
			assertEquals(1, output.closes.get());
			assertEquals(List.of("first", "second", "third"),
					output.events().stream().map(e -> e.getKey().message()).toList());
			assertEquals(0, queued(config));
		}
		finally {
			release.countDown();
			publisher.close();
		}
	}

	@Test
	void generatedBuilderReportsInvalidPropertyValues() {
		var properties = LogProperties.builder().fromProperties("""
				logging.publisher.test.bufferSize=bad
				logging.publisher.test.shutdownTimeout=bad
				""").build();
		var failure = assertThrows(LogProperty.ValidationException.class,
				() -> BatchSwapAsyncLogPublisher.builder("test").fromProperties(properties).build());
		assertEquals(
				"""
						Validation failed for io.jstach.rainbowgum.BatchSwapAsyncLogPublisherBuilder:
						Error for property. key: 'logging.publisher.test.bufferSize' from PROPERTIES_STRING[logging.publisher.test.bufferSize], java.lang.NumberFormatException For input string: "bad"
						Error for property. key: 'logging.publisher.test.shutdownTimeout' from PROPERTIES_STRING[logging.publisher.test.shutdownTimeout], java.lang.NumberFormatException For input string: "bad"\
						""",
				failure.getMessage());
	}

	enum InvalidOptions {

		BUFFER_ZERO, TIMEOUT_NEGATIVE, TIMEOUT_OVERFLOW

	}

	@ParameterizedTest
	@EnumSource
	void generatedBuilderValidatesBeforeCreatingPublisher(InvalidOptions mode) {
		var builder = BatchSwapAsyncLogPublisher.builder("test");
		String expected = switch (mode) {
			case BUFFER_ZERO -> {
				builder.bufferSize(0);
				yield """
						Validation failed for io.jstach.rainbowgum.BatchSwapAsyncLogPublisherBuilder: bufferSize must be positive.\
						""";
			}
			case TIMEOUT_NEGATIVE -> {
				builder.shutdownTimeout(Duration.ofMillis(-1));
				yield """
						Validation failed for io.jstach.rainbowgum.BatchSwapAsyncLogPublisherBuilder: shutdownTimeout must not be negative.\
						""";
			}
			case TIMEOUT_OVERFLOW -> {
				builder.shutdownTimeout(Duration.ofSeconds(Long.MAX_VALUE));
				yield """
						Validation failed for io.jstach.rainbowgum.BatchSwapAsyncLogPublisherBuilder: shutdownTimeout is too large.\
						""";
			}
		};
		assertEquals(expected, assertThrows(LogProperty.ValidationException.class, builder::build).getMessage());
	}

	private static BatchSwapAsyncLogPublisher publisher(LogConfig config, LogOutput output, int capacity,
			Duration timeout) {
		var appender = LogAppender.builder("test")
			.output(output)
			.formatter(LogFormatter.builder().message().build())
			.build();
		return (BatchSwapAsyncLogPublisher) BatchSwapAsyncLogPublisher.builder("test")
			.bufferSize(capacity)
			.shutdownTimeout(timeout)
			.build()
			.create("test", config, new LogAppender.Appenders("test", config, List.of(appender)));
	}

	private static LogEvent event(String message) {
		return LogEvent.of(Instant.EPOCH, "producer", 1, Level.INFO, "test", message, KeyValues.of(), null);
	}

	private static long queued(LogConfig config) {
		return metric(config, LogMetrics.EVENTS_QUEUED_METRIC);
	}

	private static long metric(LogConfig config, String name) {
		return config.metrics()
			.snapshot()
			.stream()
			.filter(m -> m.name().equals(name))
			.mapToLong(LogMetrics.Metric::value)
			.sum();
	}

	private static void await(CountDownLatch latch) {
		try {
			assertTrue(latch.await(10, TimeUnit.SECONDS), "latch timed out");
		}
		catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			throw new AssertionError(e);
		}
	}

	private static void join(Thread thread) throws InterruptedException {
		thread.join(10000);
		assertFalse(thread.isAlive(), "thread did not finish");
	}

	private static void awaitState(Thread thread, Thread.State expected) throws InterruptedException {
		long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
		while (thread.getState() != expected && System.nanoTime() < deadline) {
			Thread.sleep(1);
		}
		assertEquals(expected, thread.getState());
	}

	private static final class LifecycleOutput extends ListLogOutput {

		final AtomicInteger starts = new AtomicInteger();

		final AtomicInteger closes = new AtomicInteger();

		boolean failStart;

		@Override
		public void start(LogConfig config) {
			starts.incrementAndGet();
			if (failStart) {
				throw new IllegalStateException("startup failed");
			}
		}

		@Override
		public void close() {
			closes.incrementAndGet();
		}

	}

}

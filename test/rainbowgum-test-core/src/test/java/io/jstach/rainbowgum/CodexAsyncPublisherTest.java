package io.jstach.rainbowgum;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.System.Logger.Level;
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
class CodexAsyncPublisherTest {

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
					assertEquals("""
							Interrupted while waiting to publish an event.\
							""", config.alerts().dump().getFirst().message());
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
				assertEquals("""
						CodexAsyncPublisher is not running.\
						""",
						assertThrows(IllegalStateException.class, () -> publisher.log(event("late"))).getMessage());
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
		try {
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
			assertEquals("""
					CodexAsyncPublisher worker stopped before accepting the event.\
					""", java.util.Objects.requireNonNull(failure.get()).getMessage());
			assertEquals("""
					Async worker failed.\
					""", config.alerts().dump().getFirst().message());
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
		assertEquals("""
				CodexAsyncPublisher is not running.\
				""", assertThrows(IllegalStateException.class, () -> publisher.log(event("early"))).getMessage());
		publisher.start(config);
		publisher.start(config);
		publisher.log(event("one"));
		publisher.close();
		publisher.close();
		assertEquals(1, output.starts.get());
		assertEquals(1, output.closes.get());
		assertEquals(1, output.events().size());
		assertEquals("""
				CodexAsyncPublisher cannot be restarted.\
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
		var result = new AtomicReference<String>();
		output.setConsumer((event, text) -> {
			result.set(assertThrows(IllegalStateException.class, () -> publisher.log(event("recursive"))).getMessage());
			publisher.close();
		});
		publisher.start(config);
		publisher.log(event("one"));
		publisher.close();
		assertEquals("""
				Cannot publish recursively from the async worker.\
				""", result.get());
		assertEquals(1, output.events().size());
		assertEquals(1, output.closes.get());
	}

	private static CodexAsyncPublisher publisher(LogConfig config, LogOutput output, int capacity, Duration timeout) {
		var appender = LogAppender.builder("test")
			.output(output)
			.formatter(LogFormatter.builder().message().build())
			.build();
		return (CodexAsyncPublisher) CodexAsyncPublisher.builder()
			.bufferSize(capacity)
			.shutdownTimeout(timeout)
			.build()
			.create("test", config, new LogAppender.Appenders("test", config, List.of(appender)));
	}

	private static LogEvent event(String message) {
		return LogEvent.of(Instant.EPOCH, "producer", 1, Level.INFO, "test", message, KeyValues.of(), null);
	}

	private static long queued(LogConfig config) {
		return config.metrics()
			.snapshot()
			.stream()
			.filter(m -> m.name().equals(LogMetrics.EVENTS_QUEUED_METRIC))
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

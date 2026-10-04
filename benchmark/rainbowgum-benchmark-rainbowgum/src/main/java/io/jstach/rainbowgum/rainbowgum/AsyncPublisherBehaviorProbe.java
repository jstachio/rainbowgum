package io.jstach.rainbowgum.rainbowgum;

import java.lang.System.Logger.Level;
import java.time.Instant;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import io.jstach.rainbowgum.KeyValues;
import io.jstach.rainbowgum.LogConfig;
import io.jstach.rainbowgum.LogEvent;
import io.jstach.rainbowgum.LogFormatter;
import io.jstach.rainbowgum.LogMetrics;
import io.jstach.rainbowgum.LogPublisher;
import io.jstach.rainbowgum.RainbowGum;
import io.jstach.rainbowgum.output.ListLogOutput;
import io.jstach.rainbowgum.rainbowgum.AsyncPublisherBenchmark.Implementation;

/**
 * Reproducible observations of lifecycle policy, using real appenders and outputs. Run
 * with the pinned Opus implementation on the classpath, as in run-async.sh.
 */
public final class AsyncPublisherBehaviorProbe {

	private AsyncPublisherBehaviorProbe() {
	}

	/**
	 * Reports behavior without changing either implementation.
	 * @param args unused.
	 * @throws Exception if coordination fails.
	 */
	public static void main(String[] args) throws Exception {
		for (var implementation : new Implementation[] { Implementation.BATCH_SWAP, Implementation.OPUS }) {
			startupFailure(implementation);
			interruptedWithSpace(implementation);
			closeWithWaitingProducer(implementation);
		}
	}

	private static void startupFailure(Implementation implementation) {
		var output = new ObservedOutput();
		output.failStart = true;
		try (var fixture = fixture(implementation, output)) {
			String failure = "none";
			try {
				fixture.publisher.start(fixture.gum.config());
			}
			catch (RuntimeException e) {
				failure = e.toString();
			}
			fixture.publisher.close();
			System.out.printf("%s startup-failure: output-close-calls=%d; error=%s%n", implementation, output.closes,
					failure);
		}
	}

	private static void interruptedWithSpace(Implementation implementation) throws Exception {
		var output = new ObservedOutput();
		try (var fixture = fixture(implementation, output)) {
			fixture.publisher.start(fixture.gum.config());
			var interrupted = new AtomicBoolean();
			var error = new AtomicReference<Throwable>();
			var producer = Thread.ofPlatform().start(() -> {
				Thread.currentThread().interrupt();
				try {
					fixture.publisher.log(event("interrupt with space"));
				}
				catch (Throwable e) {
					error.set(e);
				}
				finally {
					interrupted.set(Thread.currentThread().isInterrupted());
				}
			});
			join(producer);
			fixture.publisher.close();
			System.out.printf(
					"%s interrupted-with-space: delivered=%d; dropped-metric=%d; interrupt-preserved=%s; alerts=%d; thrown=%s%n",
					implementation, output.events().size(), dropped(fixture), interrupted.get(),
					fixture.gum.config().alerts().dump().size(), error.get());
		}
	}

	private static void closeWithWaitingProducer(Implementation implementation) throws Exception {
		var output = new ObservedOutput();
		var writing = new CountDownLatch(1);
		var release = new CountDownLatch(1);
		output.setConsumer((event, text) -> {
			if (event.message().equals("first")) {
				writing.countDown();
				await(release);
			}
		});
		try (var fixture = fixture(implementation, output)) {
			fixture.publisher.start(fixture.gum.config());
			try {
				fixture.publisher.log(event("first"));
				await(writing);
				fixture.publisher.log(event("queued"));
				var error = new AtomicReference<Throwable>();
				var producer = Thread.ofPlatform().start(() -> {
					try {
						fixture.publisher.log(event("waiting"));
					}
					catch (Throwable e) {
						error.set(e);
					}
				});
				awaitState(producer, Thread.State.WAITING);
				var closer = Thread.ofPlatform().start(fixture.publisher::close);
				awaitState(closer, Thread.State.TIMED_WAITING);
				release.countDown();
				join(producer);
				join(closer);
				System.out.printf(
						"%s close-with-waiter: delivered=%s; dropped-metric=%d; output-close-calls=%d; thrown=%s%n",
						implementation, output.events().stream().map(e -> e.getKey().message()).toList(),
						dropped(fixture), output.closes, error.get());
			}
			finally {
				release.countDown();
			}
		}
	}

	private static long dropped(Fixture fixture) {
		return fixture.gum.config()
			.metrics()
			.snapshot()
			.stream()
			.filter(m -> m.name().equals(LogMetrics.EVENTS_DROPPED_METRIC))
			.mapToLong(LogMetrics.Metric::value)
			.sum();
	}

	private static Fixture fixture(Implementation implementation, ObservedOutput output) {
		var publisher = new AtomicReference<LogPublisher>();
		var factory = implementation.factory(1);
		var gum = RainbowGum.builder(LogConfig.builder().build()).route(r -> r.publisher((name, config, appenders) -> {
			var p = factory.create(name, config, appenders);
			publisher.set(p);
			return p;
		}).appender("probe", a -> a.output(output).formatter(LogFormatter.builder().message().build()))).build();
		return new Fixture(gum, java.util.Objects.requireNonNull(publisher.get()));
	}

	private record Fixture(RainbowGum gum, LogPublisher publisher) implements AutoCloseable {

		@Override
		public void close() {
			gum.close();
		}

	}

	private static LogEvent event(String message) {
		return LogEvent.of(Instant.EPOCH, "producer", 1, Level.INFO, "probe", message, KeyValues.of(), null);
	}

	private static void await(CountDownLatch latch) {
		try {
			if (!latch.await(5, TimeUnit.SECONDS)) {
				throw new IllegalStateException("Probe latch timed out.");
			}
		}
		catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			throw new IllegalStateException(e);
		}
	}

	private static void join(Thread thread) throws InterruptedException {
		thread.join(5000);
		if (thread.isAlive()) {
			throw new IllegalStateException("Probe thread did not finish.");
		}
	}

	private static void awaitState(Thread thread, Thread.State expected) throws InterruptedException {
		long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
		while (thread.getState() != expected && System.nanoTime() < deadline) {
			Thread.sleep(1);
		}
		if (thread.getState() != expected) {
			throw new IllegalStateException("Expected " + expected + ", got " + thread.getState());
		}
	}

	private static final class ObservedOutput extends ListLogOutput {

		boolean failStart;

		volatile int closes;

		@Override
		public void start(LogConfig config) {
			if (failStart) {
				throw new IllegalStateException("probe startup failure");
			}
		}

		@Override
		public void close() {
			closes++;
		}

	}

}

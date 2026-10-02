package io.jstach.rainbowgum.publisher;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.System.Logger.Level;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import io.jstach.rainbowgum.LogAppender;
import io.jstach.rainbowgum.LogConfig;
import io.jstach.rainbowgum.LogMetrics;
import io.jstach.rainbowgum.LogPublisher.PublisherFactory;
import io.jstach.rainbowgum.RainbowGum;
import io.jstach.rainbowgum.TestLogEventFactory;
import io.jstach.rainbowgum.output.ListLogOutput;

class BlockingQueueAsyncLogPublisherMetricsTest {

	@ParameterizedTest
	@EnumSource(Scenario.class)
	void queuedEventsTrackEnqueueDequeueAndShutdown(Scenario scenario) throws InterruptedException {
		var config = LogConfig.builder().build();
		var output = new PausedOutput(scenario == Scenario.APPENDER_FAILURE);
		var appender = LogAppender.builder("test").output(output).build().provide("test", config);
		var publisher = BlockingQueueAsyncLogPublisher.of(appender, 3, config.alerts());
		publisher.start(config);
		Thread closer = new Thread(publisher::close);
		try {
			assertEquals(0, queued(config));
			publisher.log(TestLogEventFactory.of().event("first"));
			assertTrue(output.entered.await(5, TimeUnit.SECONDS));
			assertEquals(0, queued(config), "an event being written is no longer queued");
			for (int i = 0; i < 3; i++) {
				publisher.log(TestLogEventFactory.of().event("queued " + i));
			}
			assertEquals(3, queued(config));
			if (scenario == Scenario.INTERRUPTED_PUT) {
				Thread producer = new Thread(() -> publisher.log(TestLogEventFactory.of().event("interrupted")));
				producer.start();
				producer.interrupt();
				producer.join(5000);
				assertFalse(producer.isAlive());
				assertEquals(3, queued(config), "an interrupted put must not add a queued event");
			}
			if (scenario == Scenario.CLOSE_DRAIN) {
				closer.start();
				assertTrue(output.interrupted.await(5, TimeUnit.SECONDS));
				assertEquals(3, queued(config));
			}
			output.release.countDown();
			if (scenario == Scenario.CLOSE_DRAIN) {
				closer.join(5000);
				assertFalse(closer.isAlive());
			}
			else {
				publisher.close();
			}
			assertTrue(output.closed.await(5, TimeUnit.SECONDS));
			assertEquals(0, queued(config));
			assertEquals(4, output.events().size());
		}
		finally {
			output.release.countDown();
			publisher.close();
			closer.join(5000);
		}
	}

	@Test
	void configuredAsyncPublisherUsesTheConfigurationsMetrics() throws InterruptedException {
		var config = LogConfig.builder().build();
		var output = new PausedOutput(false);
		try (var gum = RainbowGum.builder(config)
			.route(route -> route.appender("test", appender -> appender.output(output))
				.publisher(PublisherFactory.ofAsync(3)))
			.build()
			.start()) {
			try {
				gum.log(TestLogEventFactory.of().event("first"));
				assertTrue(output.entered.await(5, TimeUnit.SECONDS));
				gum.log(TestLogEventFactory.of().event("queued"));
				assertEquals(1, queued(config));
			}
			finally {
				output.release.countDown();
			}
		}
		assertTrue(output.closed.await(5, TimeUnit.SECONDS));
		assertEquals(0, queued(config));
	}

	@Test
	void multiplePublishersShareTheAggregateQueuedCount() throws InterruptedException {
		var config = LogConfig.builder().build();
		var firstOutput = new PausedOutput(false);
		var secondOutput = new PausedOutput(false);
		var firstAppender = LogAppender.builder("first").output(firstOutput).build().provide("first", config);
		var secondAppender = LogAppender.builder("second").output(secondOutput).build().provide("second", config);
		var first = BlockingQueueAsyncLogPublisher.of(firstAppender, 3, config.alerts());
		var second = BlockingQueueAsyncLogPublisher.of(secondAppender, 3, config.alerts());
		first.start(config);
		second.start(config);
		try {
			first.log(TestLogEventFactory.of().event("first"));
			second.log(TestLogEventFactory.of().event("first"));
			assertTrue(firstOutput.entered.await(5, TimeUnit.SECONDS));
			assertTrue(secondOutput.entered.await(5, TimeUnit.SECONDS));
			first.log(TestLogEventFactory.of().event("queued one"));
			second.log(TestLogEventFactory.of().event("queued two"));
			second.log(TestLogEventFactory.of().event("queued three"));
			assertEquals(3, queued(config));
		}
		finally {
			firstOutput.release.countDown();
			secondOutput.release.countDown();
			first.close();
			second.close();
		}
		assertTrue(firstOutput.closed.await(5, TimeUnit.SECONDS));
		assertTrue(secondOutput.closed.await(5, TimeUnit.SECONDS));
		assertEquals(0, queued(config));
	}

	private static long queued(LogConfig config) {
		return config.metrics()
			.counters()
			.stream()
			.filter(counter -> counter.name().equals(LogMetrics.EVENTS_QUEUED_METRIC) && counter.level() == Level.INFO)
			.findFirst()
			.orElseThrow()
			.count();
	}

	enum Scenario {

		NORMAL, APPENDER_FAILURE, INTERRUPTED_PUT, CLOSE_DRAIN

	}

	static final class PausedOutput extends ListLogOutput {

		final CountDownLatch entered = new CountDownLatch(1);

		final CountDownLatch release = new CountDownLatch(1);

		final CountDownLatch interrupted = new CountDownLatch(1);

		final CountDownLatch closed = new CountDownLatch(1);

		PausedOutput(boolean fail) {
			setConsumer((event, body) -> {
				if (!event.message().equals("first")) {
					return;
				}
				entered.countDown();
				boolean restoreInterrupt = false;
				try {
					while (true) {
						try {
							assertTrue(release.await(5, TimeUnit.SECONDS), "test did not release the output");
							break;
						}
						catch (InterruptedException e) {
							restoreInterrupt = true;
							interrupted.countDown();
						}
					}
				}
				finally {
					if (restoreInterrupt) {
						Thread.currentThread().interrupt();
					}
				}
				if (fail) {
					throw new IllegalStateException("output failed");
				}
			});
		}

		@Override
		public void close() {
			super.close();
			closed.countDown();
		}

	}

}

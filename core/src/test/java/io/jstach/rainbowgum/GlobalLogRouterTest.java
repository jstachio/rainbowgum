package io.jstach.rainbowgum;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.lang.System.Logger.Level;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;

class GlobalLogRouterTest {

	/*
	 * GlobalLogRouter.log() reads the volatile delegate, and if it is still the bootstrap
	 * QueueEventsRouter, enqueues into it. A concurrent drain (startup finishing config
	 * load, or close()) can swap delegate and fully drain the old queue in the window
	 * between that read and the enqueue - without synchronizing against drainLock, an
	 * event landing in that window is added to a queue that will never be polled again
	 * and is silently lost. This hammers log() from many threads concurrently with a
	 * drain and asserts every event is observed exactly once, whichever path
	 * (queued-then-drained, or routed directly post-drain) it took.
	 */
	@Test
	void testConcurrentLogDuringDrainDoesNotLoseEvents() throws Exception {
		// start from a clean bootstrap queue state regardless of what earlier tests
		// left GlobalLogRouter.INSTANCE in.
		GlobalLogRouter.INSTANCE.close();

		int threadCount = 8;
		int perThread = 5_000;
		int total = threadCount * perThread;

		Set<Integer> seen = ConcurrentHashMap.newKeySet();
		var publisher = new TestSyncPublisher() {
			@Override
			public void log(LogEvent event) {
				var sb = new StringBuilder();
				event.formattedMessage(sb);
				seen.add(Integer.valueOf(sb.toString()));
			}
		};
		var resolver = LevelResolver.builder().level(Level.TRACE).build();
		@SuppressWarnings("resource")
		var router = new SimpleRouter("1", publisher, resolver);
		var config = LogConfig.builder().build();
		var target = InternalRootRouter.of(List.of(router), config);

		ExecutorService executor = Executors.newFixedThreadPool(threadCount);
		var startLatch = new CountDownLatch(1);
		var doneLatch = new CountDownLatch(threadCount);
		AtomicInteger counter = new AtomicInteger();

		for (int t = 0; t < threadCount; t++) {
			executor.submit(() -> {
				try {
					startLatch.await();
					for (int i = 0; i < perThread; i++) {
						int id = counter.getAndIncrement();
						var event = LogEvent.of(Level.INFO, "stuff", String.valueOf(id), null);
						GlobalLogRouter.INSTANCE.log(event);
					}
				}
				catch (InterruptedException e) {
					Thread.currentThread().interrupt();
				}
				finally {
					doneLatch.countDown();
				}
			});
		}

		startLatch.countDown();
		// drain concurrently while the logging threads are still hammering log().
		GlobalLogRouter.INSTANCE.drain(target);

		assertEquals(true, doneLatch.await(30, TimeUnit.SECONDS), "logging threads did not finish in time");
		executor.shutdown();

		assertEquals(total, seen.size(), "expected every logged event to be observed exactly once");

		GlobalLogRouter.INSTANCE.close();
	}

}

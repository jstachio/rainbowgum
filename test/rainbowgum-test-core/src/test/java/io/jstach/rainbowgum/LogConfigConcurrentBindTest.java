package io.jstach.rainbowgum;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;

class LogConfigConcurrentBindTest {

	@Test
	void concurrentBuildsFromTheSameBuilderOnlyOneWins() throws Exception {
		for (int i = 0; i < 200; i++) {
			var builder = RainbowGum.builder(LogConfig.builder().build());
			var gate = new CountDownLatch(1);
			var wins = new AtomicInteger();
			var failures = new AtomicInteger();
			Runnable attempt = () -> {
				try {
					gate.await();
					builder.build().close();
					wins.incrementAndGet();
				}
				catch (IllegalStateException e) {
					failures.incrementAndGet();
				}
				catch (InterruptedException e) {
					Thread.currentThread().interrupt();
				}
			};
			var t1 = Thread.ofPlatform().start(attempt);
			var t2 = Thread.ofPlatform().start(attempt);
			gate.countDown();
			t1.join();
			t2.join();
			assertEquals(1, wins.get());
			assertEquals(1, failures.get());
		}
	}

}

package io.jstach.rainbowgum;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.CopyOnWriteArrayList;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.junit.jupiter.api.parallel.Isolated;

/*
 * Binds Rainbow Gum globally.
 */
@Isolated
@Execution(ExecutionMode.SAME_THREAD)
class GlobalBindTest {

	@Test
	void setSupplierDoesNotStartAGumThatWasReplaced() {
		var first = RainbowGum.builder(LogConfig.builder().build()).build();
		var start = RainbowGum.set(first);
		try (var second = RainbowGum.builder(LogConfig.builder().build()).set()) {
			var e = assertThrows(IllegalStateException.class, start::get);
			assertEquals("Another Rainbow Gum was set as the global one after this one.", e.getMessage());
			assertTrue(RainbowGum.getOrNull() == second, "the second gum must stay bound");
		}
		finally {
			first.close();
		}
	}

	@Test
	void concurrentBuilderSetReturnsTheGumEachThreadBuilt() throws InterruptedException {
		int threads = 8;
		var ready = new CountDownLatch(threads);
		var go = new CountDownLatch(1);
		List<Throwable> failures = new CopyOnWriteArrayList<>();
		List<Thread> started = new ArrayList<>();
		for (int t = 0; t < threads; t++) {
			var thread = Thread.ofPlatform().start(() -> {
				ready.countDown();
				try {
					go.await();
					for (int i = 0; i < 20; i++) {
						var config = LogConfig.builder().build();
						var gum = RainbowGum.builder(config).set();
						try {
							assertSame(config, gum.config());
						}
						finally {
							gum.close();
						}
					}
				}
				catch (Throwable e) {
					failures.add(e);
				}
			});
			started.add(thread);
		}
		ready.await();
		go.countDown();
		for (var thread : started) {
			thread.join();
		}
		assertEquals(List.of(), failures);
	}

}

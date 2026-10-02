package io.jstach.rainbowgum;

import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.junit.jupiter.api.parallel.Isolated;

/*
 * Thread based RainbowGumEntryPointTest cases, kept out of core so they do not slow
 * core's build. RainbowGumHolder is JVM-wide state, hence @Isolated and the reset
 * around each test, same as RainbowGumEntryPointTest.
 */
@Isolated
@Execution(ExecutionMode.SAME_THREAD)
class RainbowGumEntryPointConcurrencyTest {

	@BeforeEach
	void before() {
		RainbowGumHolder.remove(null);
	}

	@AfterEach
	void after() {
		RainbowGumHolder.remove(null);
	}

	@Test
	void currentDoesNotBlockAndReturnsNullWhileAnotherThreadIsResolving() throws Exception {
		var resolving = new CountDownLatch(1);
		var proceed = new CountDownLatch(1);
		var built = new AtomicReference<RainbowGum>();
		Supplier<RainbowGum> slow = () -> {
			resolving.countDown();
			try {
				proceed.await();
			}
			catch (InterruptedException e) {
				Thread.currentThread().interrupt();
			}
			var gum = RainbowGum.builder(LogConfig.builder().build()).build();
			built.set(gum);
			return gum;
		};
		RainbowGum.set(slow);
		var resolverThread = new Thread(RainbowGum::of, "rainbowgum-resolver");
		resolverThread.start();
		try {
			assertTrue(resolving.await(5, TimeUnit.SECONDS));
			// The resolver thread now holds the write lock inside
			// RainbowGumHolder.get() - current()/getOrNull() must not block on it.
			assertNull(RainbowGum.getOrNull());
		}
		finally {
			proceed.countDown();
			resolverThread.join(5000);
			var gum = built.get();
			if (gum != null) {
				gum.close();
			}
		}
	}

}

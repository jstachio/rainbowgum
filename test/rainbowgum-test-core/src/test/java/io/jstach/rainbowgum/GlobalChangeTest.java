package io.jstach.rainbowgum;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.ref.Reference;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Isolated;

import io.jstach.rainbowgum.output.ListLogOutput;

@Isolated
class GlobalChangeTest {

	@Test
	void reentrantReplacementReachesEverySubscriberInBindingOrder() {
		try (var first = gum(); var second = gum()) {
			List<RainbowGum> seen = new CopyOnWriteArrayList<>();
			Consumer<RainbowGum> replace = current -> {
				if (current == first) {
					RainbowGum.set(() -> second);
					assertSame(second, RainbowGum.of());
				}
			};
			Consumer<RainbowGum> observe = seen::add;
			RainbowGum.onGlobalChange(replace);
			RainbowGum.onGlobalChange(observe);
			RainbowGum.set(() -> first);
			assertSame(first, RainbowGum.of());
			assertSame(second, RainbowGum.getOrNull());
			assertEquals(List.of(first, second), seen);
			Reference.reachabilityFence(replace);
			Reference.reachabilityFence(observe);
		}
	}

	@Test
	void concurrentReplacementDoesNotWaitForOrOvertakeBlockedSubscriber() throws Exception {
		var entered = new CountDownLatch(1);
		var release = new CountDownLatch(1);
		try (var executor = Executors.newFixedThreadPool(2); var first = gum(); var second = gum()) {
			List<RainbowGum> seen = new CopyOnWriteArrayList<>();
			Consumer<RainbowGum> block = current -> {
				if (current == first) {
					entered.countDown();
					try {
						assertTrue(release.await(30, TimeUnit.SECONDS));
					}
					catch (InterruptedException e) {
						Thread.currentThread().interrupt();
						throw new AssertionError(e);
					}
				}
			};
			Consumer<RainbowGum> observe = seen::add;
			RainbowGum.onGlobalChange(block);
			RainbowGum.onGlobalChange(observe);
			RainbowGum.set(() -> first);
			var started = executor.submit(RainbowGum::of);
			try {
				assertTrue(entered.await(10, TimeUnit.SECONDS));
				var replaced = executor.submit(() -> {
					RainbowGum.set(() -> second);
					return RainbowGum.of();
				});
				assertSame(second, replaced.get(10, TimeUnit.SECONDS));
			}
			finally {
				release.countDown();
			}
			assertSame(first, started.get(10, TimeUnit.SECONDS));
			assertEquals(List.of(first, second), seen);
			Reference.reachabilityFence(block);
			Reference.reachabilityFence(observe);
		}
	}

	@Test
	void throwingSubscriberDoesNotHideBindingFromLaterSubscribers() {
		try (var gum = gum()) {
			Consumer<RainbowGum> fail = current -> {
				if (current == gum) {
					throw new IllegalStateException("subscriber failed");
				}
			};
			List<RainbowGum> seen = new CopyOnWriteArrayList<>();
			Consumer<RainbowGum> observe = seen::add;
			RainbowGum.onGlobalChange(fail);
			RainbowGum.onGlobalChange(observe);
			RainbowGum.set(() -> gum);
			assertSame(gum, RainbowGum.of());
			assertEquals(List.of(gum), seen);
			Reference.reachabilityFence(fail);
			Reference.reachabilityFence(observe);
		}
	}

	private static RainbowGum gum() {
		return RainbowGum.builder(LogConfig.builder().build())
			.route(r -> r.appender("list", a -> a.output(new ListLogOutput())))
			.build();
	}

}

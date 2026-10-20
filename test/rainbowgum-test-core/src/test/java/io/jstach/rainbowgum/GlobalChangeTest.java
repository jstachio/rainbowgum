package io.jstach.rainbowgum;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.ref.Reference;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Isolated;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import io.jstach.rainbowgum.output.ListLogOutput;

@Isolated
class GlobalChangeTest {

	/*
	 * A gum left bound by an earlier test would make the first change a removal.
	 */
	@BeforeEach
	void unbind() {
		RainbowGum.builder(LogConfig.builder().build()).unset();
	}

	@Test
	void reentrantReplacementReachesEverySubscriberInBindingOrder() {
		try (var first = gum(); var second = gum()) {
			List<@Nullable RainbowGum> seen = new CopyOnWriteArrayList<>();
			Consumer<@Nullable RainbowGum> replace = current -> {
				if (current == first) {
					RainbowGum.set(() -> second);
					assertSame(second, RainbowGum.of());
				}
			};
			Consumer<@Nullable RainbowGum> observe = seen::add;
			RainbowGum.onGlobalChange(replace);
			RainbowGum.onGlobalChange(observe);
			RainbowGum.set(() -> first);
			assertSame(first, RainbowGum.of());
			assertSame(second, RainbowGum.getOrNull());
			assertEquals(Arrays.asList(first, null, second), seen);
			Reference.reachabilityFence(replace);
			Reference.reachabilityFence(observe);
		}
	}

	@Test
	void concurrentReplacementDoesNotWaitForOrOvertakeBlockedSubscriber() throws Exception {
		var entered = new CountDownLatch(1);
		var release = new CountDownLatch(1);
		try (var executor = Executors.newFixedThreadPool(2); var first = gum(); var second = gum()) {
			List<@Nullable RainbowGum> seen = new CopyOnWriteArrayList<>();
			Consumer<@Nullable RainbowGum> block = current -> {
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
			Consumer<@Nullable RainbowGum> observe = seen::add;
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
			assertEquals(Arrays.asList(first, null, second), seen);
			Reference.reachabilityFence(block);
			Reference.reachabilityFence(observe);
		}
	}

	@Test
	void throwingSubscriberDoesNotHideBindingFromLaterSubscribers() {
		try (var gum = gum()) {
			Consumer<@Nullable RainbowGum> fail = current -> {
				if (current == gum) {
					throw new IllegalStateException("subscriber failed");
				}
			};
			List<@Nullable RainbowGum> seen = new CopyOnWriteArrayList<>();
			Consumer<@Nullable RainbowGum> observe = seen::add;
			RainbowGum.onGlobalChange(fail);
			RainbowGum.onGlobalChange(observe);
			RainbowGum.set(() -> gum);
			assertSame(gum, RainbowGum.of());
			assertEquals(List.of(gum), seen);
			Reference.reachabilityFence(fail);
			Reference.reachabilityFence(observe);
		}
	}

	enum Removal {

		CLOSE {
			@Override
			void remove(RainbowGum gum) {
				gum.close();
			}
		},
		UNSET {
			@Override
			void remove(RainbowGum gum) {
				RainbowGum.builder(LogConfig.builder().build()).unset();
			}
		},
		SET_SUPPLIER {
			@Override
			void remove(RainbowGum gum) {
				RainbowGum.set(GlobalChangeTest::gum);
			}
		};

		abstract void remove(RainbowGum gum);

	}

	@ParameterizedTest
	@EnumSource(Removal.class)
	void removalIsPublishedOnceAsNull(Removal removal) {
		try (var bound = gum()) {
			List<@Nullable RainbowGum> seen = new CopyOnWriteArrayList<>();
			Consumer<@Nullable RainbowGum> observe = seen::add;
			RainbowGum.onGlobalChange(observe);
			RainbowGum.set(() -> bound);
			assertSame(bound, RainbowGum.of());
			removal.remove(bound);
			assertNull(RainbowGum.getOrNull());
			removal.remove(bound);
			assertEquals(Arrays.asList(bound, null), seen);
			Reference.reachabilityFence(observe);
		}
	}

	@Test
	void reentrantRemovalIsDeliveredAfterTheBinding() {
		try (var bound = gum()) {
			List<@Nullable RainbowGum> seen = new CopyOnWriteArrayList<>();
			Consumer<@Nullable RainbowGum> close = current -> {
				if (current == bound) {
					bound.close();
				}
			};
			Consumer<@Nullable RainbowGum> observe = seen::add;
			RainbowGum.onGlobalChange(close);
			RainbowGum.onGlobalChange(observe);
			RainbowGum.set(() -> bound);
			assertSame(bound, RainbowGum.of());
			assertNull(RainbowGum.getOrNull());
			assertEquals(Arrays.asList(bound, null), seen);
			Reference.reachabilityFence(close);
			Reference.reachabilityFence(observe);
		}
	}

	private static RainbowGum gum() {
		return RainbowGum.builder(LogConfig.builder().build())
			.route(r -> r.appender("list", a -> a.output(new ListLogOutput())))
			.build();
	}

}

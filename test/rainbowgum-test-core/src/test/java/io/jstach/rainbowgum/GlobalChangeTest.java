package io.jstach.rainbowgum;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.ref.Reference;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Isolated;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import io.jstach.rainbowgum.output.ListLogOutput;

@Isolated
class GlobalChangeTest {

	/*
	 * A gum left bound by another test would make the first change a removal.
	 */
	@BeforeEach
	@AfterEach
	void reset() {
		RainbowGum.builder(LogConfig.builder().build()).unset();
	}

	@Test
	void reentrantReplacementReachesEverySubscriberInOrder() {
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

	enum Replacement {

		SET_SUPPLIER, CLOSE_THEN_SET_SUPPLIER;

	}

	@ParameterizedTest
	@EnumSource(Replacement.class)
	void concurrentReplacementDoesNotWaitForOrOvertakeBlockedSubscriber(Replacement replacement) throws Exception {
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
					if (replacement == Replacement.CLOSE_THEN_SET_SUPPLIER) {
						first.close();
					}
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

		CLOSE, UNSET, FAILING_SUPPLIER;

	}

	@ParameterizedTest
	@EnumSource(Removal.class)
	void removalIsPublishedOnceAsNull(Removal removal) {
		try (var first = gum(); var second = gum()) {
			List<@Nullable RainbowGum> seen = new CopyOnWriteArrayList<>();
			Consumer<@Nullable RainbowGum> observe = seen::add;
			RainbowGum.onGlobalChange(observe);
			RainbowGum.set(() -> first);
			RainbowGum.of();
			switch (removal) {
				case CLOSE -> first.close();
				case UNSET -> reset();
				case FAILING_SUPPLIER -> RainbowGum.set(() -> {
					throw new IllegalStateException("supplier failed");
				});
			}
			assertNull(RainbowGum.getOrNull());
			assertEquals(Arrays.asList(first, null), seen);
			if (removal == Removal.FAILING_SUPPLIER) {
				assertThrows(IllegalStateException.class, RainbowGum::of);
			}
			reset();
			first.close();
			assertEquals(Arrays.asList(first, null), seen, "removing when none is bound is not a change");
			RainbowGum.set(() -> second);
			RainbowGum.of();
			first.close();
			assertSame(second, RainbowGum.getOrNull());
			assertEquals(Arrays.asList(first, null, second), seen);
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

	@Test
	void nestedRemovalDoesNotDeliverWhileTheOuterWriteLockIsHeld() throws Exception {
		var closed = new AtomicBoolean();
		var output = new ListLogOutput() {
			@Override
			public void close() {
				if (!closed.get()) {
					reset();
					closed.set(true);
				}
			}
		};
		try (var executor = Executors.newSingleThreadExecutor(); var first = gum(output); var second = gum()) {
			RainbowGum.set(() -> first);
			RainbowGum.of();
			var once = new AtomicBoolean(true);
			Consumer<@Nullable RainbowGum> replace = current -> {
				if (current == null && once.compareAndSet(true, false)) {
					assertTrue(closed.get(), "the outer router reset must finish before delivery");
					try {
						/*
						 * Another thread can only take the write lock once no lifecycle
						 * operation holds it.
						 */
						executor.submit(() -> RainbowGum.set(() -> second)).get(10, TimeUnit.SECONDS);
					}
					catch (Exception e) {
						throw new AssertionError(e);
					}
				}
			};
			RainbowGum.onGlobalChange(replace);
			first.close();
			assertTrue(closed.get());
			assertSame(second, RainbowGum.of());
			Reference.reachabilityFence(replace);
		}
	}

	@Test
	void queuedGumCleanupDoesNotCloseAReentrantReplacement() {
		var output = new ListLogOutput();
		try (var first = RainbowGum.queued(LogConfig.builder().build()); var second = gum(output)) {
			RainbowGum.set(() -> first);
			RainbowGum.of();
			var once = new AtomicBoolean(true);
			Consumer<@Nullable RainbowGum> replace = current -> {
				if (current == null && once.compareAndSet(true, false)) {
					RainbowGum.set(() -> second);
					RainbowGum.of();
				}
			};
			RainbowGum.onGlobalChange(replace);
			first.close();
			assertSame(second, RainbowGum.getOrNull());
			logThroughGlobalRouter();
			assertEquals(1, output.events().size());
			Reference.reachabilityFence(replace);
		}
	}

	enum OlderGum {

		BUILT, QUEUED;

	}

	@ParameterizedTest
	@EnumSource(OlderGum.class)
	void closingAnOlderGumDoesNotRemoveOrResetItsReplacement(OlderGum olderGum) {
		var output = new ListLogOutput();
		try (var first = switch (olderGum) {
			case BUILT -> gum();
			case QUEUED -> RainbowGum.queued(LogConfig.builder().build());
		}; var second = gum(output)) {
			RainbowGum.set(() -> first);
			RainbowGum.of();
			RainbowGum.set(() -> second);
			RainbowGum.of();
			List<@Nullable RainbowGum> seen = new CopyOnWriteArrayList<>();
			Consumer<@Nullable RainbowGum> observe = seen::add;
			RainbowGum.onGlobalChange(observe);
			first.close();
			assertSame(second, RainbowGum.getOrNull());
			assertTrue(seen.isEmpty(), "closing an older gum must not remove the bound one");
			logThroughGlobalRouter();
			assertEquals(1, output.events().size());
			Reference.reachabilityFence(observe);
		}
	}

	@Test
	void failedOutputCloseStillRemovesTheBindingAndNotifiesSubscribers() {
		var failOnce = new AtomicBoolean(true);
		var output = new ListLogOutput() {
			@Override
			public void close() {
				if (failOnce.compareAndSet(true, false)) {
					throw new IllegalStateException("close failed");
				}
			}
		};
		try (var gum = gum(output)) {
			RainbowGum.set(() -> gum);
			RainbowGum.of();
			List<@Nullable RainbowGum> seen = new CopyOnWriteArrayList<>();
			Consumer<@Nullable RainbowGum> observe = seen::add;
			RainbowGum.onGlobalChange(observe);
			assertThrows(IllegalStateException.class, gum::close);
			assertNull(RainbowGum.getOrNull());
			assertEquals(Arrays.asList((RainbowGum) null), seen);
			Reference.reachabilityFence(observe);
		}
	}

	@Test
	void settingTheBoundRouterAgainKeepsItsSubscribers() {
		try (var gum = gum()) {
			RainbowGum.set(() -> gum);
			RainbowGum.of();
			List<LogRouter.RootRouter> seen = new CopyOnWriteArrayList<>();
			LogRouter.global().onChange(seen::add);
			InternalRootRouter.setRouter(gum.router());
			((InternalRootRouter) gum.router()).changePublisher().publish(gum.router());
			assertEquals(List.of(gum.router()), seen);
		}
	}

	private static void logThroughGlobalRouter() {
		var event = LogEventFactory.of("test").eventNoArg(System.Logger.Level.INFO, "replacement", null);
		var route = LogRouter.global().route(event.loggerName(), event.level());
		assertTrue(route.isEnabled());
		route.log(event);
	}

	private static RainbowGum gum() {
		return gum(new ListLogOutput());
	}

	private static RainbowGum gum(ListLogOutput output) {
		return RainbowGum.builder(LogConfig.builder().build())
			.route(r -> r.appender("list", a -> a.output(output)))
			.build();
	}

}

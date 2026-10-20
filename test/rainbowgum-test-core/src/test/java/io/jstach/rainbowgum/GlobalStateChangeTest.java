package io.jstach.rainbowgum;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.ref.Reference;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Isolated;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;

import io.jstach.rainbowgum.output.ListLogOutput;

@Isolated
class GlobalStateChangeTest {

	@BeforeEach
	@AfterEach
	void reset() {
		RainbowGum.builder(LogConfig.builder().build()).unset();
	}

	enum Clear {

		CLOSE, UNSET, SUPPLIER;

	}

	@ParameterizedTest
	@EnumSource(Clear.class)
	void clearIsReportedOnceWithoutChangingLegacySubscribers(Clear clear) {
		List<Optional<RainbowGum>> states = new CopyOnWriteArrayList<>();
		List<RainbowGum> bindings = new CopyOnWriteArrayList<>();
		Consumer<Optional<RainbowGum>> observe = states::add;
		Consumer<RainbowGum> legacy = bindings::add;
		RainbowGum.onGlobalStateChange(observe);
		RainbowGum.onGlobalChange(legacy);
		try (var first = gum(new ListLogOutput()); var second = gum(new ListLogOutput())) {
			RainbowGum.set(() -> first);
			RainbowGum.of();
			assertEquals(List.of(Optional.of(first)), states);
			switch (clear) {
				case CLOSE -> first.close();
				case UNSET -> reset();
				case SUPPLIER -> RainbowGum.set(() -> {
					throw new IllegalStateException("supplier failed");
				});
			}
			assertNull(RainbowGum.getOrNull());
			assertEquals(List.of(Optional.of(first), Optional.empty()), states);
			if (clear == Clear.SUPPLIER) {
				assertThrows(IllegalStateException.class, RainbowGum::of);
			}
			reset();
			first.close();
			assertEquals(2, states.size(), "clearing an empty binding is not another transition");
			RainbowGum.set(() -> second);
			RainbowGum.of();
			first.close();
			assertSame(second, RainbowGum.getOrNull());
			assertEquals(List.of(Optional.of(first), Optional.empty(), Optional.of(second)), states);
			assertEquals(List.of(first, second), bindings);
			Reference.reachabilityFence(observe);
			Reference.reachabilityFence(legacy);
		}
	}

	@Test
	void clearWhileBindingDeliveryIsBlockedRemainsOrdered() throws Exception {
		var entered = new CountDownLatch(1);
		var release = new CountDownLatch(1);
		try (var executor = Executors.newFixedThreadPool(2);
				var first = gum(new ListLogOutput());
				var second = gum(new ListLogOutput())) {
			Consumer<Optional<RainbowGum>> block = state -> {
				if (state.orElse(null) == first) {
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
			List<Optional<RainbowGum>> states = new CopyOnWriteArrayList<>();
			Consumer<Optional<RainbowGum>> observe = states::add;
			RainbowGum.onGlobalStateChange(block);
			RainbowGum.onGlobalStateChange(observe);
			RainbowGum.set(() -> first);
			var started = executor.submit(RainbowGum::of);
			try {
				assertTrue(entered.await(10, TimeUnit.SECONDS));
				var replaced = executor.submit(() -> {
					first.close();
					RainbowGum.set(() -> second);
					return RainbowGum.of();
				});
				assertSame(second, replaced.get(10, TimeUnit.SECONDS));
			}
			finally {
				release.countDown();
			}
			assertSame(first, started.get(10, TimeUnit.SECONDS));
			assertEquals(List.of(Optional.of(first), Optional.empty(), Optional.of(second)), states);
			Reference.reachabilityFence(block);
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
		try (var executor = Executors.newSingleThreadExecutor();
				var first = gum(output);
				var second = gum(new ListLogOutput())) {
			RainbowGum.set(() -> first);
			RainbowGum.of();
			var once = new AtomicBoolean(true);
			Consumer<Optional<RainbowGum>> replace = state -> {
				if (state.isEmpty() && once.compareAndSet(true, false)) {
					assertTrue(closed.get(), "the outer router reset must finish before delivery");
					try {
						executor.submit(() -> RainbowGum.set(() -> second)).get(10, TimeUnit.SECONDS);
					}
					catch (Exception e) {
						throw new AssertionError(e);
					}
				}
			};
			RainbowGum.onGlobalStateChange(replace);
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
			Consumer<Optional<RainbowGum>> replace = state -> {
				if (state.isEmpty() && once.compareAndSet(true, false)) {
					RainbowGum.set(() -> second);
					RainbowGum.of();
				}
			};
			RainbowGum.onGlobalStateChange(replace);
			first.close();
			assertSame(second, RainbowGum.getOrNull());
			var event = LogEventFactory.of("test").eventNoArg(System.Logger.Level.INFO, "replacement", null);
			var route = LogRouter.global().route(event.loggerName(), event.level());
			assertTrue(route.isEnabled());
			route.log(event);
			assertEquals(1, output.events().size());
			Reference.reachabilityFence(replace);
		}
	}

	@ParameterizedTest
	@ValueSource(booleans = { false, true })
	void closingAnOlderGumDoesNotClearOrResetItsReplacement(boolean queued) {
		var output = new ListLogOutput();
		try (var first = queued ? RainbowGum.queued(LogConfig.builder().build()) : gum(new ListLogOutput());
				var second = gum(output)) {
			RainbowGum.set(() -> first);
			RainbowGum.of();
			RainbowGum.set(() -> second);
			RainbowGum.of();
			List<Optional<RainbowGum>> states = new CopyOnWriteArrayList<>();
			Consumer<Optional<RainbowGum>> observe = states::add;
			RainbowGum.onGlobalStateChange(observe);
			first.close();
			assertSame(second, RainbowGum.getOrNull());
			assertTrue(states.isEmpty(), "closing an older gum must not clear the current binding");
			var event = LogEventFactory.of("test").eventNoArg(System.Logger.Level.INFO, "replacement", null);
			var route = LogRouter.global().route(event.loggerName(), event.level());
			assertTrue(route.isEnabled());
			route.log(event);
			assertEquals(1, output.events().size());
			Reference.reachabilityFence(observe);
		}
	}

	@Test
	void failedOutputCloseStillClearsTheBindingAndNotifiesSubscribers() {
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
			List<Optional<RainbowGum>> states = new CopyOnWriteArrayList<>();
			Consumer<Optional<RainbowGum>> observe = states::add;
			RainbowGum.onGlobalStateChange(observe);
			assertThrows(IllegalStateException.class, gum::close);
			assertNull(RainbowGum.getOrNull());
			assertEquals(List.of(Optional.empty()), states);
			Reference.reachabilityFence(observe);
		}
	}

	private static RainbowGum gum(ListLogOutput output) {
		return RainbowGum.builder(LogConfig.builder().build())
			.route(r -> r.appender("list", a -> a.output(output)))
			.build();
	}

}

package io.jstach.rainbowgum;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.lang.ref.Reference;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

import org.jspecify.annotations.Nullable;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.junit.jupiter.api.parallel.Isolated;

import io.jstach.rainbowgum.output.ListLogOutput;

/*
 * Binds Rainbow Gum globally.
 */
@Isolated
@Execution(ExecutionMode.SAME_THREAD)
class GlobalBindTest {

	private static RainbowGum currentBinding() {
		var gum = RainbowGum.getOrNull();
		if (gum == null) {
			throw new AssertionError("Expected a globally bound RainbowGum.");
		}
		return gum;
	}

	@Test
	void setSupplierDoesNotStartAGumThatWasReplaced() {
		try (var first = RainbowGum.builder(LogConfig.builder().build()).build()) {
			var start = RainbowGum.set(first);
			try (var second = RainbowGum.builder(LogConfig.builder().build()).set()) {
				var e = assertThrows(IllegalStateException.class, start::get);
				assertEquals("Another Rainbow Gum was set as the global one after this one.", e.getMessage());
				assertSame(second, currentBinding(), "the second gum must stay bound");
			}
		}
	}

	@Test
	void failedReplacementKeepsThePreviousGlobalAndClosesTheCandidate() {
		var output = new ListLogOutput();
		var changes = new ArrayList<Optional<RainbowGum>>();
		Consumer<@Nullable RainbowGum> listener = gum -> changes.add(Optional.ofNullable(gum));
		int[] closes = { 0 };
		var broken = new ListLogOutput() {
			@Override
			public void start(LogConfig config) {
				throw new IllegalStateException("start failed");
			}

			@Override
			public void close() {
				closes[0]++;
			}
		};
		try (var original = RainbowGum.builder(LogConfig.builder().build())
			.route(r -> r.appender("old", a -> a.output(output)))
			.set()) {
			RainbowGum.onGlobalChange(listener);
			changes.clear();
			var hooks = ShutdownManager.shutdownHooks();
			var error = assertThrows(IllegalStateException.class,
					() -> RainbowGum.builder(LogConfig.builder().build())
						.route(r -> r.appender("bad", a -> a.output(broken)))
						.set());
			assertEquals("start failed", error.getMessage());
			assertSame(original, currentBinding());
			assertEquals(List.of(), changes);
			LogRouter.global()
				.eventLogger("test")
				.log(LogEventFactory.of("test")
					.eventNoArg(System.Logger.Level.INFO, "still bound", KeyValues.of(), null));
			assertEquals(1, output.events().size());
			assertEquals(1, closes[0]);
			assertEquals(hooks, ShutdownManager.shutdownHooks());
		}
		finally {
			Reference.reachabilityFence(listener);
		}
	}

	@Test
	void failedRouteNotificationRestoresRoutingAndSubscribers() {
		var output = new ListLogOutput();
		var calls = new AtomicInteger();
		try (var original = RainbowGum.builder(LogConfig.builder().build())
			.route(r -> r.appender("old", a -> a.output(output)))
			.set()) {
			LogRouter.global().onChange(router -> {
				if (calls.getAndIncrement() == 0) {
					throw new IllegalStateException("route subscriber failed");
				}
			});
			var hooks = ShutdownManager.shutdownHooks();
			var error = assertThrows(IllegalStateException.class,
					() -> RainbowGum.builder(LogConfig.builder().build()).set());
			assertEquals("route subscriber failed", error.getMessage());
			assertSame(original, currentBinding());
			assertEquals(hooks, ShutdownManager.shutdownHooks());
			LogRouter.global()
				.eventLogger("test")
				.log(LogEventFactory.of("test")
					.eventNoArg(System.Logger.Level.INFO, "still bound", KeyValues.of(), null));
			assertEquals(1, output.events().size());
			assertEquals(2, calls.get());
			try (var replacement = RainbowGum.builder(LogConfig.builder().build()).set()) {
				assertSame(replacement, currentBinding());
				assertEquals(3, calls.get());
			}
		}
	}

	@Test
	void failedQueueDrainLeavesAUsableQueueForTheNextBinding() {
		RainbowGum.builder(LogConfig.builder().build()).unset();
		var event = LogEventFactory.of("test").eventNoArg(System.Logger.Level.INFO, "queued", KeyValues.of(), null);
		LogRouter.global().eventLogger("test").log(event);
		int[] closes = { 0 };
		var broken = new ListLogOutput() {
			@Override
			public void close() {
				closes[0]++;
			}
		};
		broken.setConsumer((e, text) -> {
			throw new AssertionError("queue drain failed");
		});
		var hooks = ShutdownManager.shutdownHooks();
		var error = assertThrows(AssertionError.class,
				() -> RainbowGum.builder(LogConfig.builder().build())
					.route(r -> r.appender("bad", a -> a.output(broken)))
					.set());
		assertEquals("queue drain failed", error.getMessage());
		assertEquals(1, closes[0]);
		assertEquals(hooks, ShutdownManager.shutdownHooks());
		LogRouter.global().eventLogger("test").log(event);
		var output = new ListLogOutput();
		try (var replacement = RainbowGum.builder(LogConfig.builder().build())
			.route(r -> r.appender("good", a -> a.output(output)))
			.set()) {
			assertSame(replacement, currentBinding());
			assertEquals(1, output.events().size());
		}
	}

	@Test
	void startupSeesThePreviousBindingUntilReplacementCommits() {
		try (var original = RainbowGum.builder(LogConfig.builder().build()).set()) {
			var output = new ListLogOutput() {
				@Override
				public void start(LogConfig config) {
					assertSame(original, currentBinding());
				}
			};
			try (var replacement = RainbowGum.builder(LogConfig.builder().build())
				.route(r -> r.appender("new", a -> a.output(output)))
				.set()) {
				assertSame(replacement, currentBinding());
			}
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
						try (var gum = RainbowGum.builder(config).set()) {
							assertSame(config, gum.config());
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

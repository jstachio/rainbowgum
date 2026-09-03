package io.jstach.rainbowgum;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.System.Logger.Level;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.function.Supplier;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.junit.jupiter.api.parallel.Isolated;

/*
 * RainbowGumHolder is static, JVM-wide state (the same concern JDKSetupTest in
 * rainbowgum-test-jdk documents for LogRouter.global()), so every test resets it before
 * and after itself rather than relying solely on try-with-resources, in case a prior test
 * in the same JVM left it dirty or this one fails mid-test.
 *
 * A couple of RainbowGum's methods (queued(LogConfig), getOrNull(), set(RainbowGum)) look
 * unused from core's own coverage report, but are real - they're only called from the
 * Spring integration modules (PreBootRainbowGumProvider, RainbowGumLoggingSystemFactory),
 * whose tests don't contribute to core's jacoco report.
 *
 * RainbowGumTest also touches RainbowGumHolder/GlobalLogRouter.INSTANCE - confirmed by
 * running the "fast" profile's parallel suite, which raced the two classes against each
 * other (RainbowGumEntryPointTest saw a different instance than it just set, and a missed
 * IllegalStateException; RainbowGumTest saw stray shutdown hooks). @Isolated is required,
 * not just SAME_THREAD.
 */
@Isolated
@Execution(ExecutionMode.SAME_THREAD)
class RainbowGumEntryPointTest {

	@BeforeEach
	void before() {
		RainbowGumHolder.remove(null);
	}

	@AfterEach
	void after() {
		RainbowGumHolder.remove(null);
	}

	@Test
	void getOrNullIsNullBeforeAnythingIsSet() {
		assertNull(RainbowGum.getOrNull());
	}

	@Test
	void getOrNullReturnsTheCurrentlySetGumWithoutBlockingOrLoading() {
		var config = LogConfig.builder().build();
		try (var gum = RainbowGum.builder(config).set()) {
			var current = RainbowGum.getOrNull();
			if (current == null) {
				throw new AssertionError("expected a RainbowGum to be currently set");
			}
			assertSame(gum, current);
		}
	}

	@Test
	void defaultsBuildsAFreshInstanceViaServiceLoaderWithoutTouchingTheGlobalHolder() {
		var gum = RainbowGum.defaults();
		try {
			// defaults() builds independently of RainbowGum.of()'s lazily-provisioned
			// global instance - it must not register itself as the global one.
			assertNull(RainbowGum.getOrNull());
		}
		finally {
			gum.close();
		}
	}

	@Test
	void builderWithConsumerAppliesConfigurationToTheBuiltGum() {
		Map<String, String> props = Map.of("logging.level.stuff", "DEBUG");
		var gum = RainbowGum.builder(b -> b.properties(props::get)).build();
		try {
			boolean debugEnabled = gum.router().route("stuff", Level.DEBUG).isEnabled();
			assertTrue(debugEnabled);
		}
		finally {
			gum.close();
		}
	}

	@Test
	void queuedUsesTheGlobalRouter() {
		var config = LogConfig.builder().build();
		var gum = RainbowGum.queued(config);
		assertSame(LogRouter.global(), gum.router());
	}

	@Test
	void setWithValueReturnsASupplierThatGetsTheSameInstance() {
		var config = LogConfig.builder().build();
		var gum = RainbowGum.builder(config).build();
		var supplier = RainbowGum.set(gum);
		try {
			assertSame(gum, supplier.get());
		}
		finally {
			gum.close();
		}
	}

	@Test
	void setWithValueThrowsIfAnotherGumWasRegisteredBeforeSupplierIsInvoked() {
		var gumA = RainbowGum.builder(LogConfig.builder().build()).build();
		var gumB = RainbowGum.builder(LogConfig.builder().build()).build();
		var supplierForA = RainbowGum.set(gumA);
		try {
			// Simulate a race: something else registers a different gum before
			// supplierForA is actually invoked.
			RainbowGum.set(() -> gumB);
			assertThrows(IllegalStateException.class, supplierForA::get);
		}
		finally {
			gumA.close();
			gumB.close();
		}
	}

	@Test
	void builderUnsetRemovesCurrentlySetGumRegardlessOfWhatItIs() {
		var builder = RainbowGum.builder(LogConfig.builder().build());
		var gum = builder.set();
		try {
			if (RainbowGum.getOrNull() == null) {
				throw new AssertionError("expected a RainbowGum to be currently set");
			}
			builder.unset();
			assertNull(RainbowGum.getOrNull());
		}
		finally {
			// unset() does not close the gum it removed from the holder - do it
			// ourselves so its resources/shutdown hook do not leak into other tests.
			gum.close();
		}
	}

	@Test
	void builderOptionalAlwaysReturnsABuiltGum() {
		var config = LogConfig.builder().build();
		var optional = RainbowGum.builder(config).optional();
		assertTrue(optional.isPresent());
		optional.get().close();
	}

	@Test
	void builderOptionalWithTrueConditionBuilds() {
		var config = LogConfig.builder().build();
		var optional = RainbowGum.builder(config).optional(c -> true);
		assertTrue(optional.isPresent());
		optional.get().close();
	}

	@Test
	void builderOptionalWithFalseConditionIsEmpty() {
		var config = LogConfig.builder().build();
		var optional = RainbowGum.builder(config).optional(c -> false);
		assertTrue(optional.isEmpty());
	}

	@Test
	void startingTwiceThrows() {
		var gum = RainbowGum.builder(LogConfig.builder().build()).build();
		try {
			gum.start();
			var ex = assertThrows(IllegalStateException.class, gum::start);
			String message = Objects.requireNonNullElse(ex.getMessage(), "");
			assertTrue(message.contains("started"), message);
		}
		finally {
			gum.close();
		}
	}

	@Test
	void toStringIncludesInstanceIdAndState() {
		var gum = RainbowGum.builder(LogConfig.builder().build()).build();
		try {
			String beforeStart = gum.toString();
			assertTrue(beforeStart.contains("created"), beforeStart);
			gum.start();
			String afterStart = gum.toString();
			assertTrue(afterStart.contains("started"), afterStart);
			assertTrue(afterStart.contains(gum.instanceId().toString()), afterStart);
			gum.close();
			String afterClose = gum.toString();
			assertTrue(afterClose.contains("closed"), afterClose);
		}
		finally {
			gum.close();
		}
	}

	@Test
	void reentrantOfWhileResolvingThrowsTriedToLogTooEarly() {
		Supplier<RainbowGum> reentrant = () -> {
			// Re-enters RainbowGumHolder.get() while the write lock from the outer
			// RainbowGum.of() call (below) is still held by this same thread.
			RainbowGum.of();
			throw new AssertionError("unreachable - the reentrant call above always throws first");
		};
		RainbowGum.set(reentrant);
		var ex = assertThrows(IllegalStateException.class, RainbowGum::of);
		String message = Objects.requireNonNullElse(ex.getMessage(), "");
		assertTrue(message.contains("too early"), message);
	}

	@Test
	void ofASecondTimeTakesTheFastAlreadyResolvedPath() {
		try (var gum = RainbowGum.builder(LogConfig.builder().build()).set()) {
			// The first RainbowGum.of() (inside set()) resolved and started it; this
			// call should hit RainbowGumHolder.get()'s read-lock fast path instead of
			// resolving via the supplier again.
			assertSame(gum, RainbowGum.of());
		}
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

	@Test
	void reentrantSetWhileResolvingThrowsTriedToLogTooEarly() {
		RainbowGum.set(() -> {
			// Re-enters RainbowGumHolder.set() while the write lock from the outer
			// RainbowGum.of() call (below) is still held by this same thread.
			RainbowGum.set(() -> {
				throw new AssertionError("unreachable - never invoked");
			});
			throw new AssertionError("unreachable - the reentrant set() call above always throws first");
		});
		var ex = assertThrows(IllegalStateException.class, RainbowGum::of);
		String message = Objects.requireNonNullElse(ex.getMessage(), "");
		assertTrue(message.contains("too early"), message);
	}

	@Test
	void closingWithoutStartingIsANoop() {
		var gum = RainbowGum.builder(LogConfig.builder().build()).build();
		// Should not throw and should not register/require a shutdown hook removal.
		gum.close();
	}

	/*
	 * The main use case this exists for: SLF4J's logger factory (or anything else
	 * bootstrapped once, early) finding out that a different RainbowGum has since become
	 * the global one, without needing to be reconstructed.
	 */
	@Test
	void onGlobalChangeNotifiesSubscribersWhenANewGumBecomesGlobal() {
		var notified = new AtomicReference<RainbowGum>();
		Consumer<RainbowGum> consumer = notified::set;
		RainbowGum.onGlobalChange(consumer);
		try (var gum = RainbowGum.builder(LogConfig.builder().build()).set()) {
			assertSame(gum, notified.get());
		}
	}

	/*
	 * RainbowGumHolder.get()'s read-lock fast path (an already-resolved instance) must
	 * not re-publish - only the write-lock path that actually resolves/starts a new
	 * instance does.
	 */
	@Test
	void onGlobalChangeIsNotFiredAgainWhenOfResolvesTheAlreadyBoundInstance() {
		var callCount = new AtomicInteger();
		Consumer<RainbowGum> consumer = gum -> callCount.incrementAndGet();
		RainbowGum.onGlobalChange(consumer);
		try (var gum = RainbowGum.builder(LogConfig.builder().build()).set()) {
			assertEquals(1, callCount.get());
			RainbowGum.of();
			assertEquals(1, callCount.get());
		}
	}

	@Test
	void onGlobalChangeIsNotFiredWhenNothingEverResolvesTheSupplier() {
		var callCount = new AtomicInteger();
		Consumer<RainbowGum> consumer = gum -> callCount.incrementAndGet();
		RainbowGum.onGlobalChange(consumer);
		var gum = RainbowGum.builder(LogConfig.builder().build()).build();
		try {
			// set(RainbowGum) only registers a supplier - nothing has called
			// RainbowGum.of() to actually resolve/start it yet.
			RainbowGum.set(gum);
			assertEquals(0, callCount.get());
		}
		finally {
			gum.close();
		}
	}

}

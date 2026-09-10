package io.jstach.rainbowgum.signal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledOnOs;
import org.junit.jupiter.api.condition.OS;

import io.jstach.rainbowgum.LogConfig;
import io.jstach.rainbowgum.LogProperties;
import io.jstach.rainbowgum.LogResponse;
import io.jstach.rainbowgum.RainbowGum;
import io.jstach.rainbowgum.output.ListLogOutput;

import sun.misc.Signal;
import sun.misc.SignalHandler;

@DisabledOnOs(OS.WINDOWS)
class SignalConfiguratorTest {

	@Test
	void testDisabledByDefaultDoesNotInstallHandler() {
		var output = countingOutput(new CountDownLatch(1), new AtomicInteger());

		var config = LogConfig.builder().configurator(new SignalConfigurator()).build();
		var gum = RainbowGum.builder(config).route(r -> r.appender("app", a -> a.output(output))).build();

		try (var g = gum.start()) {
			/*
			 * sun.misc.Signal.raise() throws IllegalArgumentException("Unhandled signal")
			 * rather than delivering anything at the OS level when the JVM has no
			 * internal dispatch trap registered for that signal at all - confirmed via a
			 * scratch test that this is also exactly the state Signal.handle(sig,
			 * SIG_DFL) restores things to, so this assertion is safe/deterministic
			 * regardless of what earlier tests in this class did.
			 */
			assertThrows(IllegalArgumentException.class,
					() -> Signal.raise(new Signal(SignalConfigurator.DEFAULT_SIGNAL_NAME)),
					"handler must not be installed by default");
		}
	}

	@Test
	void testSignalEnablePropertyInstallsHandler() throws InterruptedException {
		var reopenCount = new AtomicInteger();
		var latch = new CountDownLatch(1);
		var output = countingOutput(latch, reopenCount);

		var properties = LogProperties.builder().fromProperties(SignalConfigurator.SIGNAL_ENABLE + "=true").build();
		var config = LogConfig.builder().properties(properties).configurator(new SignalConfigurator()).build();
		var gum = RainbowGum.builder(config).route(r -> r.appender("app", a -> a.output(output))).build();

		try (var g = gum.start()) {
			/*
			 * Signal.raise dispatches to the JVM's signal handler thread asynchronously -
			 * raise() returning does NOT mean the handler ran yet.
			 */
			Signal.raise(new Signal(SignalConfigurator.DEFAULT_SIGNAL_NAME));
			assertTrue(latch.await(5, TimeUnit.SECONDS), "signal handler should have run within 5s");
			assertEquals(1, reopenCount.get());
		}
	}

	@Test
	void testProgrammaticEnabledInstallsHandlerWithoutProperty() throws InterruptedException {
		var reopenCount = new AtomicInteger();
		var latch = new CountDownLatch(1);
		var output = countingOutput(latch, reopenCount);

		var config = LogConfig.builder().configurator(new SignalConfigurator().enabled(true)).build();
		var gum = RainbowGum.builder(config).route(r -> r.appender("app", a -> a.output(output))).build();

		try (var g = gum.start()) {
			Signal.raise(new Signal(SignalConfigurator.DEFAULT_SIGNAL_NAME));
			assertTrue(latch.await(5, TimeUnit.SECONDS), "signal handler should have run within 5s");
			assertEquals(1, reopenCount.get());
		}
	}

	@Test
	void testProgrammaticDisabledOverridesEnabledProperty() {
		var output = countingOutput(new CountDownLatch(1), new AtomicInteger());

		var properties = LogProperties.builder().fromProperties(SignalConfigurator.SIGNAL_ENABLE + "=true").build();
		var config = LogConfig.builder()
			.properties(properties)
			.configurator(new SignalConfigurator().enabled(false))
			.build();
		var gum = RainbowGum.builder(config).route(r -> r.appender("app", a -> a.output(output))).build();

		try (var g = gum.start()) {
			assertThrows(IllegalArgumentException.class,
					() -> Signal.raise(new Signal(SignalConfigurator.DEFAULT_SIGNAL_NAME)),
					"explicit enabled(false) must override the property");
		}
	}

	@Test
	void testCloseRestoresPreviousHandler() {
		var config = LogConfig.builder().configurator(new SignalConfigurator().enabled(true)).build();
		var gum = RainbowGum.builder(config).build();

		try (var g = gum.start()) {
			// configurator now owns the signal handler.
		}
		// close() -> serviceRegistry().close() -> configurator.close() -> restore.

		Signal sig = new Signal(SignalConfigurator.DEFAULT_SIGNAL_NAME);
		SignalHandler probe = s -> {
		};
		SignalHandler restored = Signal.handle(sig, probe);
		assertEquals(SignalHandler.SIG_DFL, restored);
		Signal.handle(sig, SignalHandler.SIG_DFL); // avoid leaking `probe` across tests
													// in this JVM
	}

	private static ListLogOutput countingOutput(CountDownLatch latch, AtomicInteger reopenCount) {
		return new ListLogOutput() {
			@Override
			public LogResponse.Status reopen() {
				reopenCount.incrementAndGet();
				latch.countDown();
				return LogResponse.Status.StandardStatus.OK;
			}
		};
	}

}

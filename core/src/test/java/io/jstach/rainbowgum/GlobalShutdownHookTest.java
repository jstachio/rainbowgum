package io.jstach.rainbowgum;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.junit.jupiter.api.parallel.Isolated;

/*
 * Binds Rainbow Gum globally and inspects the shared shutdown hooks.
 */
@Isolated
@Execution(ExecutionMode.SAME_THREAD)
class GlobalShutdownHookTest {

	@Test
	void startingTheGlobalGumRegistersAShutdownHookByDefault() {
		try (var gum = RainbowGum.builder(LogConfig.builder().build()).set()) {
			assertTrue(ShutdownManager.shutdownHooks().stream().anyMatch(h -> h == gum));
		}
	}

	@Test
	void shutdownHookCanBeTurnedOff() {
		var config = LogConfig.builder()
			.properties(LogProperties.builder().fromProperties("logging.global.shutdownHook=false").build())
			.build();
		try (var gum = RainbowGum.builder(config).set()) {
			assertFalse(ShutdownManager.shutdownHooks().stream().anyMatch(h -> h == gum));
		}
	}

}

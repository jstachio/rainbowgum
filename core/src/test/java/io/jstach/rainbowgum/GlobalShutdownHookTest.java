package io.jstach.rainbowgum;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.junit.jupiter.api.parallel.Isolated;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import io.jstach.rainbowgum.LogConfig.Globals.ShutdownHookMode;

/*
 * Binds Rainbow Gum globally and inspects the shared shutdown hooks.
 */
@Isolated
@Execution(ExecutionMode.SAME_THREAD)
class GlobalShutdownHookTest {

	private static LogConfig config(String properties) {
		return LogConfig.builder().properties(LogProperties.builder().fromProperties(properties).build()).build();
	}

	private static boolean hooked(RainbowGum gum) {
		return ShutdownManager.shutdownHooks().stream().anyMatch(h -> h == gum);
	}

	@Test
	void startingTheGlobalGumRegistersAShutdownHookByDefault() {
		try (var gum = RainbowGum.builder(LogConfig.builder().build()).set()) {
			assertTrue(hooked(gum));
		}
	}

	@Test
	void shutdownHookCanBeTurnedOff() {
		try (var gum = RainbowGum.builder(config("logging.global.shutdownHook=false")).set()) {
			assertFalse(hooked(gum));
		}
	}

	@Test
	void shutdownHookCanBeTurnedOffInCode() {
		var config = LogConfig.builder().shutdownHook(ShutdownHookMode.OFF).build();
		assertEquals(ShutdownHookMode.OFF, config.globals().shutdownHook());
		try (var gum = RainbowGum.builder(config).set()) {
			assertFalse(hooked(gum));
		}
	}

	@Test
	void propertyWinsOverTheBuilder() {
		var config = LogConfig.builder()
			.properties(LogProperties.builder().fromProperties("logging.global.shutdownHook=on").build())
			.shutdownHook(ShutdownHookMode.OFF)
			.build();
		assertEquals(ShutdownHookMode.ON, config.globals().shutdownHook());
	}

	@ParameterizedTest
	@CsvSource({ "on, ON", "off, OFF", "true, ON", "false, OFF", "OFF, OFF" })
	void modesAndBooleanAliases(String value, ShutdownHookMode expected) {
		assertEquals(expected, config("logging.global.shutdownHook=" + value).globals().shutdownHook());
	}

	@Test
	void anInvalidModeFailsTheConfig() {
		var e = assertThrows(RuntimeException.class, () -> config("logging.global.shutdownHook=yes"));
		String message = String.valueOf(e.getMessage());
		assertTrue(message.contains("logging.global.shutdownHook"), message);
	}

}

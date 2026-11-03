package io.jstach.rainbowgum;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

/*
 * Reads are recorded only as long as the check needs them, so they do not grow with the
 * number of loggers an application creates.
 */
class UnusedKeyRecordingTest {

	private static final LogProperties SET = LogProperties.builder().fromProperties("""
			logging.appender.console.flags=immediate_flush
			logging.level.com.example=DEBUG
			""").build();

	private static UnusedKeyCheck check() {
		return new UnusedKeyCheck(List.of(new UnusedKeyCheck.KeySource(SET,
				() -> List.of("logging.appender.console.flags", "logging.level.com.example"))));
	}

	@Test
	void loggerNameKeysAreNotRecorded() {
		var check = check();
		var properties = check.wrap(SET);
		properties.valueOrNull("logging.level.com.example");
		properties.valueOrNull("logging.level");
		properties.valueOrNull("logging.level.root");
		properties.valueOrNull("logging.route.default.level.com.example");
		assertFalse(check.recorded("logging.level.com.example"));
		assertFalse(check.recorded("logging.route.default.level.com.example"));
		assertTrue(check.recorded("logging.level"));
		assertTrue(check.recorded("logging.level.root"));
	}

	@Test
	void readsAfterStopAreNotRecorded() {
		var check = check();
		var properties = check.wrap(SET);
		check.stopRecording();
		properties.valueOrNull("logging.appender.console.flags");
		assertFalse(check.recorded("logging.appender.console.flags"));
		assertEquals(List.of("logging.appender.console.flags"), check.unused().stream().map(u -> u.key()).toList());
	}

	@Test
	void startStopsRecording() {
		try (var gum = RainbowGum
			.builder(LogConfig.builder()
				.properties(LogProperties.builder().fromProperties("").build())
				.alertsFail(LogAlerts.FailLevel.WARNING)
				.build())
			.build()
			.start()) {
			var check = ((DefaultLogConfig) gum.config()).unusedKeyCheck();
			if (check == null) {
				throw new AssertionError("reads should be recorded when warnings fail");
			}
			assertTrue(check.recorded("logging.appender.console.encoder.uri"));
			gum.config().properties().valueOrNull("logging.example.late");
			assertFalse(check.recorded("logging.example.late"));
		}
	}

}

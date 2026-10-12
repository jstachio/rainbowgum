package io.jstach.rainbowgum.jfr;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import io.jstach.rainbowgum.LogConfig;
import io.jstach.rainbowgum.LogProperties;
import io.jstach.rainbowgum.LogProperty;

import jdk.jfr.Recording;
import jdk.jfr.consumer.RecordedEvent;
import jdk.jfr.consumer.RecordingFile;

class JfrAlertListenerConfiguratorTest {

	private static LogConfig configWith(Map<String, String> map) {
		var props = LogProperties.builder().fromFunction(map::get).build();
		return LogConfig.builder().properties(props).serviceLoader().build();
	}

	@Test
	void testDisabledByDefaultRecordsNothing(@TempDir Path dir) throws IOException {
		Path recordingFile = dir.resolve("test.jfr");
		try (Recording recording = new Recording()) {
			recording.enable(RainbowGumLogEvent.ErrorEvent.class);
			recording.start();

			var config = configWith(Map.of());
			config.alerts().error(RuntimeException.class, "boom", new RuntimeException("boom"));

			recording.stop();
			recording.dump(recordingFile);
		}

		List<RecordedEvent> events = RecordingFile.readAllEvents(recordingFile);
		assertTrue(events.isEmpty(), "Got: " + events);
	}

	@Test
	void testEnabledPropertyRecordsAlert(@TempDir Path dir) throws IOException {
		Path recordingFile = dir.resolve("test.jfr");
		try (Recording recording = new Recording()) {
			recording.enable(RainbowGumLogEvent.ErrorEvent.class);
			recording.start();

			var config = configWith(Map.of(JfrAlertListenerBuilder.PROPERTY_mode, "on"));
			config.alerts().error(RuntimeException.class, "boom", new RuntimeException("boom"));

			recording.stop();
			recording.dump(recordingFile);
		}

		List<RecordedEvent> events = RecordingFile.readAllEvents(recordingFile);
		assertEquals(1, events.size(), "expected exactly one recorded event: " + events);
	}

	@ParameterizedTest
	@CsvSource({ "on, ON", "off, OFF", "true, ON", "false, OFF", "ON, ON" })
	void testModeValues(String value, JfrAlertListener.Mode expected) {
		var props = LogProperties.builder()
			.fromFunction(Map.of(JfrAlertListenerBuilder.PROPERTY_mode, value)::get)
			.build();
		assertEquals(expected, new JfrAlertListenerBuilder().fromProperties(props).build().mode());
	}

	@Test
	void testBadModeIsAnError() {
		var props = LogProperties.builder()
			.fromFunction(Map.of(JfrAlertListenerBuilder.PROPERTY_mode, "yes")::get)
			.build();
		var e = assertThrows(LogProperty.ValidationException.class,
				() -> new JfrAlertListenerBuilder().fromProperties(props).build());
		assertEquals(
				"""
						Validation failed for io.jstach.rainbowgum.jfr.JfrAlertListenerBuilder:
						Error for property. key: 'logging.jfr.alerts.mode' from function[logging.jfr.alerts.mode], 'yes' is not a valid value for io.jstach.rainbowgum.jfr.JfrAlertListener.Mode. Valid values: 'off', 'on', 'true', 'false'""",
				e.getMessage());
	}

}

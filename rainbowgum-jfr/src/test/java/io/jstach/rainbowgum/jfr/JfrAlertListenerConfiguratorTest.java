package io.jstach.rainbowgum.jfr;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import io.jstach.rainbowgum.LogConfig;
import io.jstach.rainbowgum.LogProperties;

import jdk.jfr.Recording;
import jdk.jfr.consumer.RecordedEvent;
import jdk.jfr.consumer.RecordingFile;

class JfrAlertListenerConfiguratorTest {

	@BeforeAll
	static void warmupJfr() {
		JfrTestSupport.warmup();
	}

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

			var config = configWith(Map.of(JfrAlertListenerBuilder.PROPERTY_enabled, "true"));
			config.alerts().error(RuntimeException.class, "boom", new RuntimeException("boom"));

			recording.stop();
			recording.dump(recordingFile);
		}

		List<RecordedEvent> events = RecordingFile.readAllEvents(recordingFile);
		assertEquals(1, events.size(), "expected exactly one recorded event: " + events);
	}

}

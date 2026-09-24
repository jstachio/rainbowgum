package io.jstach.rainbowgum.jfr;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.lang.System.Logger.Level;
import java.nio.file.Path;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import io.jstach.rainbowgum.KeyValues;
import io.jstach.rainbowgum.LogEvent;

import jdk.jfr.Recording;
import jdk.jfr.consumer.RecordedEvent;
import jdk.jfr.consumer.RecordingFile;

class JfrAlertListenerTest {

	@BeforeAll
	static void warmupJfr() {
		JfrTestSupport.warmup();
	}

	@Test
	void testAlertIsRecordedAsErrorEvent(@TempDir Path dir) throws IOException {
		Path recordingFile = dir.resolve("test.jfr");
		try (Recording recording = new Recording()) {
			recording.enable(RainbowGumAlertEvent.ErrorEvent.class);
			recording.start();

			var listener = new JfrAlertListenerBuilder().build();
			Instant instant = Instant.ofEpochMilli(1);
			LogEvent e = LogEvent
				.of(instant, "main", 1L, Level.ERROR, "io.jstach.rainbowgum.LogAppender", "appender failed",
						KeyValues.of(), null)
				.freeze(instant);
			listener.onAlert(e);

			recording.stop();
			recording.dump(recordingFile);
		}

		List<RecordedEvent> events = RecordingFile.readAllEvents(recordingFile);
		assertEquals(1, events.size(), "expected exactly one recorded event: " + events);
		RecordedEvent recorded = events.get(0);
		assertEquals("io.jstach.rainbowgum.AlertError", recorded.getEventType().getName());
		assertEquals("appender failed - {}", recorded.getValue("message"));
		assertEquals("io.jstach.rainbowgum.LogAppender", recorded.getValue("logger"));
	}

	@Test
	void testAlertRendersKeyValuesInMessage(@TempDir Path dir) throws IOException {
		Path recordingFile = dir.resolve("test.jfr");
		try (Recording recording = new Recording()) {
			recording.enable(RainbowGumAlertEvent.ErrorEvent.class);
			recording.start();

			var listener = new JfrAlertListenerBuilder().build();
			Instant instant = Instant.ofEpochMilli(1);
			var map = new LinkedHashMap<String, String>();
			map.put("appender", "console");
			LogEvent e = LogEvent
				.of(instant, "main", 1L, Level.ERROR, "io.jstach.rainbowgum.LogAppender", "write failed",
						KeyValues.of(map), null)
				.freeze(instant);
			listener.onAlert(e);

			recording.stop();
			recording.dump(recordingFile);
		}

		List<RecordedEvent> events = RecordingFile.readAllEvents(recordingFile);
		assertEquals(1, events.size());
		assertEquals("write failed - {appender=console}", events.get(0).getValue("message"));
	}

	@Test
	void testThrowableIsCapturedAsString(@TempDir Path dir) throws IOException {
		Path recordingFile = dir.resolve("test.jfr");
		try (Recording recording = new Recording()) {
			recording.enable(RainbowGumAlertEvent.ErrorEvent.class);
			recording.start();

			var listener = new JfrAlertListenerBuilder().build();
			Instant instant = Instant.ofEpochMilli(1);
			Throwable t = new RuntimeException("boom");
			LogEvent e = LogEvent
				.of(instant, "main", 1L, Level.ERROR, "io.jstach.rainbowgum.LogAppender", "failed", KeyValues.of(), t)
				.freeze(instant);
			listener.onAlert(e);

			recording.stop();
			recording.dump(recordingFile);
		}

		List<RecordedEvent> events = RecordingFile.readAllEvents(recordingFile);
		assertEquals(1, events.size());
		String throwable = events.get(0).getValue("throwable");
		assertNotNull(throwable);
		assertTrue(throwable.contains("java.lang.RuntimeException: boom"), "Got: " + throwable);
	}

	@Test
	void testNoActiveRecordingDoesNotThrow() {
		var listener = new JfrAlertListenerBuilder().build();
		Instant instant = Instant.ofEpochMilli(1);
		LogEvent e = LogEvent
			.of(instant, "main", 1L, Level.ERROR, "io.jstach.rainbowgum.LogAppender", "failed", KeyValues.of(), null)
			.freeze(instant);
		listener.onAlert(e);
	}

	@Test
	void testAlertEventTypeIsDistinctFromLogOutputEventType(@TempDir Path dir) throws IOException {
		// Enables JfrLogOutput's own event type and explicitly disables
		// JfrAlertListener's (both default enabled) - proves the two are genuinely
		// separate JFR event types, not aliases of one another.
		Path recordingFile = dir.resolve("test.jfr");
		try (Recording recording = new Recording()) {
			recording.enable(RainbowGumLogEvent.ErrorEvent.class);
			recording.disable(RainbowGumAlertEvent.ErrorEvent.class);
			recording.start();

			var listener = new JfrAlertListenerBuilder().build();
			Instant instant = Instant.ofEpochMilli(1);
			LogEvent e = LogEvent
				.of(instant, "main", 1L, Level.ERROR, "io.jstach.rainbowgum.LogAppender", "failed", KeyValues.of(),
						null)
				.freeze(instant);
			listener.onAlert(e);

			recording.stop();
			recording.dump(recordingFile);
		}

		List<RecordedEvent> events = RecordingFile.readAllEvents(recordingFile);
		assertTrue(events.isEmpty(), "Got: " + events);
	}

}

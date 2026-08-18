package io.jstach.rainbowgum.jfr;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.lang.System.Logger.Level;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import io.jstach.rainbowgum.KeyValues;
import io.jstach.rainbowgum.LogConfig;
import io.jstach.rainbowgum.LogEvent;
import io.jstach.rainbowgum.LogFormatter;
import io.jstach.rainbowgum.LogMessageFormatter.StandardMessageFormatter;
import io.jstach.rainbowgum.LogOutput.ContentType.StandardContentType;
import io.jstach.rainbowgum.LogOutput.WriteMethod;

import jdk.jfr.Recording;
import jdk.jfr.consumer.RecordedEvent;
import jdk.jfr.consumer.RecordingFile;

class JfrLogOutputTest {

	@Test
	void testInfoEventIsRecorded(@TempDir Path dir) throws IOException {
		Path recordingFile = dir.resolve("test.jfr");
		try (Recording recording = new Recording()) {
			recording.enable(RainbowGumLogEvent.InfoEvent.class);
			recording.start();

			var output = new JfrLogOutput();
			Instant instant = Instant.ofEpochMilli(1);
			LogEvent e = LogEvent
				.ofAll(instant, "main", 1L, Level.INFO, "jfr-test", "hello", KeyValues.of(), null,
						StandardMessageFormatter.SLF4J, List.of())
				.freeze(instant);
			var config = LogConfig.builder().build();
			var encoder = LogFormatter.builder().message().encoder().build().provide("test", config);
			output.write(new LogEvent[] { e }, 1, encoder);

			recording.stop();
			recording.dump(recordingFile);
		}

		List<RecordedEvent> events = RecordingFile.readAllEvents(recordingFile);
		assertEquals(1, events.size(), "expected exactly one recorded event: " + events);
		RecordedEvent recorded = events.get(0);
		assertEquals("io.jstach.rainbowgum.Info", recorded.getEventType().getName());
		assertEquals("hello", recorded.getValue("message"));
		assertEquals("jfr-test", recorded.getValue("logger"));
	}

	@Test
	void testDisabledEventTypeIsNotRecorded(@TempDir Path dir) throws IOException {
		Path recordingFile = dir.resolve("test.jfr");
		try (Recording recording = new Recording()) {
			// Only INFO is enabled; DEBUG defaults to disabled.
			recording.enable(RainbowGumLogEvent.InfoEvent.class);
			recording.start();

			var output = new JfrLogOutput();
			Instant instant = Instant.ofEpochMilli(1);
			LogEvent e = LogEvent
				.ofAll(instant, "main", 1L, Level.DEBUG, "jfr-test", "should not be recorded", KeyValues.of(), null,
						StandardMessageFormatter.SLF4J, List.of())
				.freeze(instant);
			output.write(e, "should not be recorded");

			recording.stop();
			recording.dump(recordingFile);
		}

		List<RecordedEvent> events = RecordingFile.readAllEvents(recordingFile);
		assertTrue(events.isEmpty(), "Got: " + events);
	}

	@Test
	void testKeyValuesAreCapturedAsPercentEncodedString(@TempDir Path dir) throws IOException {
		Path recordingFile = dir.resolve("test.jfr");
		try (Recording recording = new Recording()) {
			recording.enable(RainbowGumLogEvent.InfoEvent.class);
			recording.start();

			var output = new JfrLogOutput();
			Instant instant = Instant.ofEpochMilli(1);
			var map = new LinkedHashMap<String, String>();
			map.put("trace id", "abc/123");
			map.put("empty", "");
			LogEvent e = LogEvent.of(instant, "main", 1L, Level.INFO, "jfr-test", "hello", KeyValues.of(map), null)
				.freeze(instant);
			output.write(e, "hello");

			recording.stop();
			recording.dump(recordingFile);
		}

		List<RecordedEvent> events = RecordingFile.readAllEvents(recordingFile);
		assertEquals(1, events.size());
		assertEquals("trace%20id=abc%2F123&empty=", events.get(0).getValue("keyValues"));
	}

	@Test
	void testThrowableIsCapturedAsString(@TempDir Path dir) throws IOException {
		Path recordingFile = dir.resolve("test.jfr");
		try (Recording recording = new Recording()) {
			recording.enable(RainbowGumLogEvent.ErrorEvent.class);
			recording.start();

			var output = new JfrLogOutput();
			Instant instant = Instant.ofEpochMilli(1);
			Throwable t = new RuntimeException("boom");
			LogEvent e = LogEvent.of(instant, "main", 1L, Level.ERROR, "jfr-test", "failed", KeyValues.of(), t)
				.freeze(instant);
			output.write(e, "failed");

			recording.stop();
			recording.dump(recordingFile);
		}

		List<RecordedEvent> events = RecordingFile.readAllEvents(recordingFile);
		assertEquals(1, events.size());
		String throwable = events.get(0).getValue("throwable");
		assertTrue(throwable.contains("java.lang.RuntimeException: boom"), "Got: " + throwable);
	}

	@Test
	void testNoActiveRecordingDoesNotThrow() {
		var output = new JfrLogOutput();
		Instant instant = Instant.ofEpochMilli(1);
		LogEvent e = LogEvent
			.ofAll(instant, "main", 1L, Level.INFO, "jfr-test", "hello", KeyValues.of(), null,
					StandardMessageFormatter.SLF4J, List.of())
			.freeze(instant);
		output.write(e, "hello");
	}

	@Test
	void testBufferHintsPreferString() {
		var output = new JfrLogOutput();
		assertEquals(WriteMethod.STRING, output.bufferHints());
	}

	@Test
	void testByteWriteFallsBackToStringMessage(@TempDir Path dir) throws IOException {
		Path recordingFile = dir.resolve("test.jfr");
		try (Recording recording = new Recording()) {
			recording.enable(RainbowGumLogEvent.InfoEvent.class);
			recording.start();

			var output = new JfrLogOutput();
			Instant instant = Instant.ofEpochMilli(1);
			LogEvent e = LogEvent.of(instant, "main", 1L, Level.INFO, "jfr-test", "ignored", KeyValues.of(), null)
				.freeze(instant);
			byte[] bytes = "encoded".getBytes(StandardCharsets.UTF_8);
			output.write(e, bytes, 0, bytes.length, StandardContentType.TEXT_PLAIN);

			recording.stop();
			recording.dump(recordingFile);
		}

		List<RecordedEvent> events = RecordingFile.readAllEvents(recordingFile);
		assertEquals(1, events.size());
		assertEquals("encoded", events.get(0).getValue("message"));
	}

}

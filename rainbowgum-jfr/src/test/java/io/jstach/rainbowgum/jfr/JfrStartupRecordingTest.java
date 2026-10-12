package io.jstach.rainbowgum.jfr;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.File;
import java.io.IOException;
import java.lang.System.Logger.Level;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import io.jstach.rainbowgum.KeyValues;
import io.jstach.rainbowgum.LogConfig;
import io.jstach.rainbowgum.LogEvent;
import io.jstach.rainbowgum.LogProperties;
import io.jstach.rainbowgum.RainbowGum;

import jdk.jfr.consumer.RecordedEvent;
import jdk.jfr.consumer.RecordingFile;

/*
 * The documented way to record: a recording started with -XX:StartFlightRecording before
 * any Rainbow Gum event class loads. That needs a fresh JVM, since in this one the event
 * classes may already be loaded by another test.
 */
class JfrStartupRecordingTest {

	@Test
	void logAndAlertEventsAreRecordedWhenTheRecordingStartsWithTheJvm(@TempDir Path dir) throws Exception {
		Path recordingFile = dir.resolve("startup.jfr");
		String classPath = Stream.of(System.getProperty("java.class.path"), System.getProperty("jdk.module.path"))
			.filter(p -> p != null && !p.isEmpty())
			.reduce((a, b) -> a + File.pathSeparator + b)
			.orElseThrow();
		var process = new ProcessBuilder(Path.of(System.getProperty("java.home"), "bin", "java").toString(),
				"-XX:StartFlightRecording:filename=" + recordingFile, "-cp", classPath, Main.class.getName())
			.redirectErrorStream(true)
			.start();
		String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
		assertEquals(true, process.waitFor(60, TimeUnit.SECONDS), output);
		assertEquals(0, process.exitValue(), output);
		assertEquals("", output.lines().filter(l -> l.contains("[jfr,system")).reduce("", (a, b) -> a + b + "\n"));
		assertEquals(Map.of("io.jstach.rainbowgum.AlertError", 1L, "io.jstach.rainbowgum.Error", 1L,
				"io.jstach.rainbowgum.Info", 1L, "io.jstach.rainbowgum.Warn", 1L), counts(recordingFile));
	}

	private static Map<String, Long> counts(Path recordingFile) throws IOException {
		var counts = new TreeMap<String, Long>();
		for (RecordedEvent e : RecordingFile.readAllEvents(recordingFile)) {
			String name = e.getEventType().getName();
			if (name.startsWith("io.jstach.rainbowgum.")) {
				counts.merge(name, 1L, Long::sum);
			}
		}
		return counts;
	}

	/*
	 * Logs one event at each default enabled level through the JFR output and raises one
	 * error alert with the JFR alert listener enabled.
	 */
	public static final class Main {

		public static void main(String[] args) {
			var properties = LogProperties.builder().fromProperties("""
					logging.jfr.alerts.enabled=true
					logging.appender.console.output.uri=jfr:///
					""").build();
			var config = LogConfig.builder().properties(properties).serviceLoader().build();
			try (var gum = RainbowGum.builder(config).build().start()) {
				for (Level level : List.of(Level.DEBUG, Level.INFO, Level.WARNING, Level.ERROR)) {
					var route = gum.router().route("app", level);
					if (route.isEnabled()) {
						route.log(LogEvent.of(Instant.now(), "main", 1, level, "app", "hello", KeyValues.of(), null));
					}
				}
				config.alerts().error(Main.class, "boom", new RuntimeException("boom"));
			}
		}

	}

}

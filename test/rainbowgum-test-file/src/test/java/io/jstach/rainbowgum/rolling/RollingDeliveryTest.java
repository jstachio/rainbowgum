package io.jstach.rainbowgum.rolling;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.IOException;
import java.lang.System.Logger.Level;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;

import io.jstach.rainbowgum.KeyValues;
import io.jstach.rainbowgum.LogConfig;
import io.jstach.rainbowgum.LogEncoder;
import io.jstach.rainbowgum.LogEvent;
import io.jstach.rainbowgum.LogFormatter;
import io.jstach.rainbowgum.LogMetrics;
import io.jstach.rainbowgum.LogProperties;
import io.jstach.rainbowgum.file.DataSize;

class RollingDeliveryTest {

	@TempDir
	Path directory;

	enum Mode {

		BUFFERED, UNBUFFERED, PRUDENT

	}

	@ParameterizedTest
	@EnumSource(Mode.class)
	void archiveFailurePreservesTheTriggeringEventAndTheRestOfTheBatch(Mode mode) throws IOException {
		Path active = directory.resolve("app.log");
		Path obstruction = Files.createDirectory(directory.resolve("app.log.3"));
		Files.writeString(obstruction.resolve("keep"), "obstruction");
		var config = LogConfig.builder().properties(LogProperties.StandardProperties.EMPTY).build();
		var formatter = LogFormatter.builder().message().newline().build();
		var encoder = LogEncoder.of(formatter).provide("file", config);
		try (var output = RollingFileOutput
			.of(b -> b.fileName(active.toString())
				.append(false)
				.prudent(mode == Mode.PRUDENT)
				.bufferSize(mode == Mode.UNBUFFERED ? DataSize.ZERO : DataSize.ofBytes(8192))
				.maxFileSize(DataSize.ofBytes(5))
				.maxHistory(3))
			.provide("file", config)) {
			output.start(config);
			var events = new LogEvent[] { event("first"), event("second"), event("third"), event("ignored") };
			assertDoesNotThrow(() -> output.write(events, 3, encoder));
			output.flush();
			assertEquals("first\nsecond\nthird\n", Files.readString(active));
			assertEquals(2, metric(config, LogMetrics.ROLL_FAIL_METRIC));
			assertEquals(0, metric(config, LogMetrics.EVENTS_FAILED_METRIC));
			assertEquals("ERROR Failed to roll file '<DIR>/app.log'\n".repeat(2),
					config.alerts()
						.dump()
						.stream()
						.map(e -> e.level() + " " + e.message() + "\n")
						.collect(Collectors.joining())
						.replace(directory.toString(), "<DIR>")
						.replace('\\', '/'));
			Files.delete(obstruction.resolve("keep"));
			Files.delete(obstruction);
			output.reopen();
			output.write(event("recovered"), "recovered\n");
			output.flush();
			assertEquals("first\nsecond\nthird\n", Files.readString(directory.resolve("app.log.1")));
			assertEquals("recovered\n", Files.readString(active));
		}
	}

	@ParameterizedTest
	@ValueSource(booleans = { false, true })
	void persistentRotationFailuresRetryByGrowthRatherThanPerEvent(boolean gzip) throws IOException {
		Path active = directory.resolve("app.log");
		Path obstruction = Files.createDirectory(directory.resolve(gzip ? "app.log.7.gz" : "app.log.7"));
		Files.writeString(obstruction.resolve("keep"), "obstruction");
		var config = LogConfig.builder().properties(LogProperties.StandardProperties.EMPTY).build();
		try (var output = RollingFileOutput
			.of(b -> b.fileName(active.toString())
				.maxFileSize(DataSize.ofBytes(1))
				.fileNamePattern(gzip ? ".%i.gz" : ".%i"))
			.provide("file", config)) {
			output.start(config);
			for (int i = 0; i < 1024; i++) {
				output.write(event("x"), "x");
			}
			output.flush();
			assertEquals("x".repeat(1024), Files.readString(active));
			assertEquals(10, metric(config, LogMetrics.ROLL_FAIL_METRIC));
			assertEquals(0, metric(config, LogMetrics.EVENTS_FAILED_METRIC));
			assertEquals(10, config.alerts().dump().size());
			Files.delete(obstruction.resolve("keep"));
			Files.delete(obstruction);
			output.reopen();
			output.write(event("recovered"), "recovered");
			output.flush();
			assertEquals("recovered", Files.readString(active));
		}
	}

	@Test
	void anEncodingFailureDoesNotDiscardLaterEventsInTheBatch() throws IOException {
		Path active = directory.resolve("app.log");
		var config = LogConfig.builder().properties(LogProperties.StandardProperties.EMPTY).build();
		LogFormatter.EventFormatter formatter = (buffer, event) -> {
			if (event.message().equals("bad")) {
				throw new IllegalArgumentException("Synthetic encoding failure");
			}
			buffer.append(event.message()).append('\n');
		};
		var encoder = LogEncoder.of(formatter).provide("file", config);
		try (var output = RollingFileOutput.of(b -> b.fileName(active.toString())).provide("file", config)) {
			output.start(config);
			assertDoesNotThrow(
					() -> output.write(new LogEvent[] { event("first"), event("bad"), event("last") }, 3, encoder));
			output.flush();
			assertEquals("first\nlast\n", Files.readString(active));
			assertEquals(1, metric(config, LogMetrics.EVENTS_FAILED_METRIC));
			assertEquals(0, metric(config, LogMetrics.ROLL_FAIL_METRIC));
			assertEquals(1, config.alerts().dump().size());
			var alert = config.alerts().dump().getFirst();
			assertEquals("Failed to append event to rolling file '" + active + "'", alert.message());
			assertEquals("java.lang.IllegalArgumentException: Synthetic encoding failure",
					String.valueOf(alert.throwableOrNull()));
		}
	}

	private static LogEvent event(String message) {
		return LogEvent.of(Instant.EPOCH, "main", 1, Level.INFO, "file", message, KeyValues.of(), null);
	}

	private static long metric(LogConfig config, String name) {
		return config.metrics().snapshot().stream().filter(c -> c.name().equals(name)).mapToLong(c -> c.value()).sum();
	}

}

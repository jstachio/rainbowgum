package io.jstach.rainbowgum.rolling;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.lang.System.Logger.Level;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import io.jstach.rainbowgum.KeyValues;
import io.jstach.rainbowgum.LogConfig;
import io.jstach.rainbowgum.LogEncoder;
import io.jstach.rainbowgum.LogEvent;
import io.jstach.rainbowgum.LogFormatter;
import io.jstach.rainbowgum.LogMetrics;
import io.jstach.rainbowgum.LogProperties;
import io.jstach.rainbowgum.file.FileOutput;

class RollingBatchFailureTest {

	@TempDir
	Path directory;

	@Test
	void failedReopenLosesOnlyItsEventAndAllowsTheBatchToRecover() {
		Path active = directory.resolve("app.log");
		var contents = new ByteArrayOutputStream();
		var opens = new AtomicInteger();
		var config = LogConfig.builder().properties(LogProperties.StandardProperties.EMPTY).build();
		Supplier<FileOutput> supplier = () -> {
			int attempt = opens.incrementAndGet();
			if (attempt <= 2) {
				throw new UncheckedIOException(new IOException("Synthetic open failure " + attempt));
			}
			return new Capture(active.toUri(), contents);
		};
		var encoder = LogEncoder.of(LogFormatter.builder().message().newline().build()).provide("file", config);
		try (var output = new DefaultRollingFileOutput(active, RollingPolicy.ParsedPattern.parse(".%i"), 1, 7, 0, false,
				supplier, new Capture(active.toUri(), contents), config)) {
			output.start(config);
			var events = new LogEvent[] { event("first"), event("failed"), event("last") };
			assertDoesNotThrow(() -> output.write(events, events.length, encoder));
			assertEquals("first\nlast\n", contents.toString(StandardCharsets.UTF_8));
			assertEquals(3, opens.get());
			assertEquals(1, metric(config, LogMetrics.EVENTS_FAILED_METRIC));
			assertEquals(1, metric(config, LogMetrics.ROLL_FAIL_METRIC));
			assertEquals(2, config.alerts().dump().size());
			var appendFailure = config.alerts().dump().getLast();
			assertEquals("Failed to append event to rolling file '" + active + "'", appendFailure.message());
			var cause = java.util.Objects.requireNonNull(appendFailure.throwableOrNull());
			assertEquals("java.io.UncheckedIOException: java.io.IOException: Synthetic open failure 2",
					cause.toString());
			assertEquals(1, cause.getSuppressed().length);
			assertEquals("java.io.UncheckedIOException: java.io.IOException: Synthetic open failure 1",
					cause.getSuppressed()[0].toString());
		}
	}

	private static LogEvent event(String text) {
		return LogEvent.of(Instant.EPOCH, "main", 1, Level.INFO, "file", text, KeyValues.of(), null);
	}

	private static long metric(LogConfig config, String name) {
		return config.metrics().snapshot().stream().filter(c -> c.name().equals(name)).mapToLong(c -> c.value()).sum();
	}

	private record Capture(URI uri, ByteArrayOutputStream contents) implements FileOutput {

		@Override
		public void write(LogEvent event, byte[] bytes, int off, int len, ContentType type) {
			contents.write(bytes, off, len);
		}

		@Override
		public void flush() {
		}

		@Override
		public void close() {
		}

	}

}

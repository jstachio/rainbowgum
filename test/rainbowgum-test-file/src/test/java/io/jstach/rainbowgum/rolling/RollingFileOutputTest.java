package io.jstach.rainbowgum.rolling;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.lang.System.Logger.Level;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.zip.GZIPInputStream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import io.jstach.rainbowgum.LogAppender.AppenderType;
import io.jstach.rainbowgum.LogConfig;
import io.jstach.rainbowgum.LogFormatter;
import io.jstach.rainbowgum.LogMetrics.StandardMetric;
import io.jstach.rainbowgum.LogProperties;
import io.jstach.rainbowgum.LogProviderRef;
import io.jstach.rainbowgum.RainbowGum;
import io.jstach.rainbowgum.TestLogEventFactory;
import io.jstach.rainbowgum.file.DataSize;

/*
 * Real appender/RainbowGum end to end - not just the pure algorithm RollingPolicyTest
 * (in rainbowgum-rolling itself) covers - kept in its own slow test module (mirroring
 * FileOutputTest's own split out of core) since actually writing/rolling/reading real
 * files is much slower than the rest of the reactor's unit tests.
 */
class RollingFileOutputTest {

	@TempDir
	Path dir;

	private static final LogFormatter FORMATTER = LogFormatter.builder().message().newline().build();

	enum FileMode {

		BUFFERED, UNBUFFERED, PRUDENT;

		LogConfig config() {
			return LogConfig.builder().properties(LogProperties.builder().fromProperties("""
					logging.output.file.prudent=%s
					logging.output.file.bufferSize=%s
					logging.output.file.append=false
					""".formatted(this == PRUDENT, this == UNBUFFERED ? 0 : 8192)).build()).build();
		}

	}

	@ParameterizedTest
	@EnumSource(FileMode.class)
	void failedArchiveRotationReportsAlertsAndMetricsAndRecovers(FileMode mode) throws IOException {
		Path active = dir.resolve("app.log");
		Files.writeString(active, "discard on initial open");
		Path obstruction = Files.createDirectory(dir.resolve("app.log.7"));
		Files.writeString(obstruction.resolve("keep"), "obstruction");
		var config = mode.config();
		var provider = RollingFileOutput.of(b -> b.fileName(active.toString()).maxFileSize(DataSize.ofBytes(5)));
		var gum = RainbowGum.builder(config)
			.route(r -> r.appender("file",
					a -> a.output(provider).formatter(FORMATTER).appenderType(AppenderType.REUSE_BUFFER)))
			.build();

		try (var rg = gum.start()) {
			config.alerts().clear();
			rg.log(TestLogEventFactory.of().event("first"));
			rg.log(TestLogEventFactory.of().event("lost"));
			rg.log(TestLogEventFactory.of().event("also lost"));
			assertEquals("first\n", Files.readString(active));
			assertEquals("""
					ERROR io.jstach.rainbowgum.rolling.DefaultRollingFileOutput: Failed to roll file '<DIR>/app.log'
					java.io.UncheckedIOException: java.nio.file.DirectoryNotEmptyException: <DIR>/app.log.7
					ERROR io.jstach.rainbowgum.ReuseBufferLogAppender: appender 'file' failed to append event
					java.io.UncheckedIOException: java.nio.file.DirectoryNotEmptyException: <DIR>/app.log.7
					""".repeat(2), errorAlerts(config));
			assertEquals(2, metric(config, StandardMetric.ROLL_FAIL));
			assertEquals(2, metric(config, StandardMetric.EVENTS_FAILED));
			assertEquals("""
					events.failed=2
					io.jstach.rainbowgum.ReuseBufferLogAppender=2
					io.jstach.rainbowgum.rolling.DefaultRollingFileOutput=2
					roll.fail=2
					""",
					config.metrics()
						.counters()
						.stream()
						.filter(c -> c.level() == Level.ERROR)
						.map(c -> c.name() + "=" + c.count() + "\n")
						.sorted()
						.collect(Collectors.joining()));
			assertEquals(0, metric(config, StandardMetric.REOPEN_FAIL));
			assertEquals(0, metric(config, StandardMetric.REOPEN));

			Files.delete(obstruction.resolve("keep"));
			Files.delete(obstruction);
			rg.log(TestLogEventFactory.of().event("next"));
			assertEquals(java.util.List.of(), config.outputRegistry().flush());
			assertEquals("first\n", Files.readString(dir.resolve("app.log.1")));
			assertEquals("next\n", Files.readString(active));
			assertEquals(2, metric(config, StandardMetric.ROLL_FAIL));
			assertEquals(4, config.alerts().dump().size());
		}
	}

	@ParameterizedTest
	@EnumSource(FileMode.class)
	void failureAfterActiveFileMovedDoesNotRotateAgainDuringRecovery(FileMode mode) throws IOException {
		Path active = dir.resolve("app.log");
		Files.writeString(dir.resolve("app.log.2"), "older\n");
		Path obstruction = Files.createDirectory(dir.resolve("app.log.4"));
		Files.writeString(obstruction.resolve("keep"), "obstruction");
		var config = mode.config();
		var provider = RollingFileOutput
			.of(b -> b.fileName(active.toString()).maxFileSize(DataSize.ofBytes(5)).totalSizeCap(DataSize.ofBytes(6)));
		var gum = RainbowGum.builder(config)
			.route(r -> r.appender("file",
					a -> a.output(provider).formatter(FORMATTER).appenderType(AppenderType.REUSE_BUFFER)))
			.build();
		try (var rg = gum.start()) {
			config.alerts().clear();
			rg.log(TestLogEventFactory.of().event("first"));
			rg.log(TestLogEventFactory.of().event("lost"));
			assertEquals("""
					ERROR io.jstach.rainbowgum.rolling.DefaultRollingFileOutput: Failed to roll file '<DIR>/app.log'
					java.io.UncheckedIOException: java.nio.file.DirectoryNotEmptyException: <DIR>/app.log.5
					ERROR io.jstach.rainbowgum.ReuseBufferLogAppender: appender 'file' failed to append event
					java.io.UncheckedIOException: java.nio.file.DirectoryNotEmptyException: <DIR>/app.log.5
					""", errorAlerts(config));
			assertFalse(Files.exists(active));
			Files.createDirectory(active);
			rg.log(TestLogEventFactory.of().event("lost during recovery"));
			assertEquals("""
					ERROR io.jstach.rainbowgum.rolling.DefaultRollingFileOutput: Failed to roll file '<DIR>/app.log'
					java.io.UncheckedIOException: java.nio.file.DirectoryNotEmptyException: <DIR>/app.log.5
					ERROR io.jstach.rainbowgum.ReuseBufferLogAppender: appender 'file' failed to append event
					java.io.UncheckedIOException: java.nio.file.DirectoryNotEmptyException: <DIR>/app.log.5
					ERROR io.jstach.rainbowgum.rolling.DefaultRollingFileOutput: Failed to roll file '<DIR>/app.log'
					java.io.UncheckedIOException: java.io.FileNotFoundException: <DIR>/app.log (<OS_REASON>)
					ERROR io.jstach.rainbowgum.ReuseBufferLogAppender: appender 'file' failed to append event
					java.io.UncheckedIOException: java.io.FileNotFoundException: <DIR>/app.log (<OS_REASON>)
					""", errorAlerts(config));
			Files.delete(active);
			rg.log(TestLogEventFactory.of().event("next"));
			assertEquals(java.util.List.of(), config.outputRegistry().flush());
			assertEquals("first\n", Files.readString(dir.resolve("app.log.1")));
			assertFalse(Files.exists(dir.resolve("app.log.2")));
			assertEquals("next\n", Files.readString(active));
			assertEquals(2, metric(config, StandardMetric.ROLL_FAIL));
			assertEquals(2, metric(config, StandardMetric.EVENTS_FAILED));
			assertEquals(0, metric(config, StandardMetric.REOPEN_FAIL));
		}
	}

	@ParameterizedTest
	@EnumSource(FileMode.class)
	void gzipFailurePreservesActiveFileAndRecovers(FileMode mode) throws IOException {
		Path active = dir.resolve("app.log");
		var config = mode.config();
		var provider = RollingFileOutput
			.of(b -> b.fileName(active.toString()).maxFileSize(DataSize.ofBytes(5)).fileNamePattern(".%i/archive.gz"));
		var gum = RainbowGum.builder(config)
			.route(r -> r.appender("file",
					a -> a.output(provider).formatter(FORMATTER).appenderType(AppenderType.REUSE_BUFFER)))
			.build();
		try (var rg = gum.start()) {
			config.alerts().clear();
			rg.log(TestLogEventFactory.of().event("first"));
			rg.log(TestLogEventFactory.of().event("lost"));
			// The temporary compression filename is nondeterministic.
			String alerts = errorAlerts(config).replaceAll("archive\\.gz[0-9]+\\.tmp", "<TEMP>");
			assertEquals("""
					ERROR io.jstach.rainbowgum.rolling.DefaultRollingFileOutput: Failed to roll file '<DIR>/app.log'
					java.io.UncheckedIOException: java.nio.file.NoSuchFileException: <DIR>/app.log.1/<TEMP>
					ERROR io.jstach.rainbowgum.ReuseBufferLogAppender: appender 'file' failed to append event
					java.io.UncheckedIOException: java.nio.file.NoSuchFileException: <DIR>/app.log.1/<TEMP>
					""", alerts);
			assertEquals("first\n", Files.readString(active));
			Files.createDirectory(dir.resolve("app.log.1"));
			rg.log(TestLogEventFactory.of().event("next"));
			assertEquals(java.util.List.of(), config.outputRegistry().flush());
			try (var in = new GZIPInputStream(Files.newInputStream(dir.resolve("app.log.1/archive.gz")))) {
				assertEquals("first\n", new String(in.readAllBytes(), StandardCharsets.UTF_8));
			}
			try (var files = Files.list(dir.resolve("app.log.1"))) {
				assertEquals(java.util.List.of("archive.gz"), files.map(p -> p.getFileName().toString()).toList());
			}
			assertEquals("next\n", Files.readString(active));
			assertEquals(1, metric(config, StandardMetric.ROLL_FAIL));
			assertEquals(1, metric(config, StandardMetric.EVENTS_FAILED));
		}
	}

	@ParameterizedTest
	@EnumSource(FileMode.class)
	void externalReopenRefreshesSizeAndReportsFailuresSeparately(FileMode mode) throws IOException {
		Path active = dir.resolve("app.log");
		var config = mode.config();
		var provider = RollingFileOutput.of(b -> b.fileName(active.toString()).maxFileSize(DataSize.ofBytes(5)));
		var gum = RainbowGum.builder(config)
			.route(r -> r.appender("file",
					a -> a.output(provider).formatter(FORMATTER).appenderType(AppenderType.REUSE_BUFFER)))
			.build();
		try (var rg = gum.start()) {
			config.alerts().clear();
			rg.log(TestLogEventFactory.of().event("first"));
			config.outputRegistry().flush();
			Files.move(active, dir.resolve("app.log.external"));
			Files.createDirectory(active);
			var errors = config.outputRegistry().reopen();
			assertEquals(1, errors.size());
			assertEquals("""
					ERROR io.jstach.rainbowgum.ReuseBufferLogAppender: appender 'file' failed to reopen output
					java.io.UncheckedIOException: java.io.FileNotFoundException: <DIR>/app.log (<OS_REASON>)
					""", errorAlerts(config));
			rg.log(TestLogEventFactory.of().event("lost"));
			assertEquals(0, metric(config, StandardMetric.ROLL_FAIL));
			assertEquals(1, metric(config, StandardMetric.EVENTS_FAILED));
			Files.delete(active);
			assertEquals(java.util.List.of(), config.outputRegistry().reopen());
			rg.log(TestLogEventFactory.of().event("next"));
			assertEquals(java.util.List.of(), config.outputRegistry().flush());
			assertEquals("first\n", Files.readString(dir.resolve("app.log.external")));
			assertEquals("next\n", Files.readString(active));
			assertFalse(Files.exists(dir.resolve("app.log.1")));
			assertEquals(0, metric(config, StandardMetric.ROLL_FAIL));
			assertEquals(1, metric(config, StandardMetric.EVENTS_FAILED));
			assertEquals(1, metric(config, StandardMetric.REOPEN_FAIL));
			assertEquals(2, metric(config, StandardMetric.REOPEN));
		}
	}

	@ParameterizedTest
	@EnumSource(FileMode.class)
	void closedOutputDoesNotRotateOrReopenOnLateWrite(FileMode mode) throws IOException {
		Path active = dir.resolve("app.log");
		var config = mode.config();
		try (var output = RollingFileOutput.of(b -> b.fileName(active.toString()).maxFileSize(DataSize.ofBytes(5)))
			.provide("file", config)) {
			output.start(config);
			var event = TestLogEventFactory.of().event("first");
			output.write(event, "first\n");
			output.close();
			output.write(event, "late\n");
			output.reopen();
			output.flush();
			assertEquals("first\n", Files.readString(active));
			assertFalse(Files.exists(dir.resolve("app.log.1")));
			assertEquals(0, metric(config, StandardMetric.ROLL_FAIL));
		}
	}

	@Test
	void zeroHistoryDoesNotDeleteArchiveZero() throws IOException {
		Path active = dir.resolve("app.log");
		Files.writeString(dir.resolve("app.log.0"), "unrelated");
		var config = LogConfig.builder().build();
		var provider = RollingFileOutput
			.of(b -> b.fileName(active.toString()).maxFileSize(DataSize.ofBytes(5)).maxHistory(0));
		var gum = RainbowGum.builder(config)
			.route(r -> r.appender("file", a -> a.output(provider).formatter(FORMATTER)))
			.build();
		try (var rg = gum.start()) {
			rg.log(TestLogEventFactory.of().event("first"));
			rg.log(TestLogEventFactory.of().event("next"));
			config.outputRegistry().flush();
			assertEquals("next\n", Files.readString(active));
			assertEquals("unrelated", Files.readString(dir.resolve("app.log.0")));
			assertFalse(Files.exists(dir.resolve("app.log.1")));
			assertEquals(0, metric(config, StandardMetric.ROLL_FAIL));
		}
	}

	private String errorAlerts(LogConfig config) {
		return config.alerts().dump().stream().filter(e -> e.level() == Level.ERROR).map(e -> {
			var throwable = Objects.requireNonNull(e.throwableOrNull());
			return e.level() + " " + e.loggerName() + ": " + e.message() + "\n" + throwable + "\n";
		})
			.collect(Collectors.joining())
			.replace(dir.toString(), "<DIR>")
			// FileNotFoundException's reason text is supplied by the operating system.
			.replaceAll("app\\.log \\([^\\r\\n]*\\)", "app.log (<OS_REASON>)");
	}

	private static long metric(LogConfig config, StandardMetric metric) {
		return config.metrics()
			.counters()
			.stream()
			.filter(c -> c.name().equals(metric.metricName()) && c.level() == metric.level())
			.mapToLong(c -> c.count())
			.sum();
	}

	@Test
	void rollsWhenMaxFileSizeExceededAndPreservesAllEventsInOrder() throws IOException {
		Path active = dir.resolve("app.log");
		var provider = RollingFileOutput.of(b -> {
			b.fileName(active.toString());
			b.maxFileSize(DataSize.ofBytes(50));
			b.maxHistory(20);
		});
		var config = LogConfig.builder().build();
		var gum = RainbowGum.builder(config)
			.route(r -> r.appender("file", a -> a.output(provider).formatter(FORMATTER)))
			.build();

		int lineCount = 50;
		try (var rg = gum.start()) {
			for (int i = 0; i < lineCount; i++) {
				rg.log(TestLogEventFactory.of().event(lineFor(i)));
			}
			rg.config().outputRegistry().flush();
		}

		assertTrue(Files.exists(dir.resolve("app.log.1")), "at least one roll must have happened");

		StringBuilder reconstructed = new StringBuilder();
		for (int n = 20; n >= 1; n--) {
			Path archive = dir.resolve("app.log." + n);
			if (Files.exists(archive)) {
				reconstructed.append(Files.readString(archive));
			}
		}
		reconstructed.append(Files.readString(active));

		StringBuilder expected = new StringBuilder();
		for (int i = 0; i < lineCount; i++) {
			expected.append(lineFor(i)).append('\n');
		}
		assertEquals(expected.toString(), reconstructed.toString(),
				"every event, across the active file and however many archives, must reconstruct in original order");
	}

	@Test
	void maxHistoryLimitsArchiveCount() throws IOException {
		Path active = dir.resolve("app.log");
		var provider = RollingFileOutput.of(b -> {
			b.fileName(active.toString());
			b.maxFileSize(DataSize.ofBytes(10));
			b.maxHistory(2);
		});
		var config = LogConfig.builder().build();
		var gum = RainbowGum.builder(config)
			.route(r -> r.appender("file", a -> a.output(provider).formatter(FORMATTER)))
			.build();

		try (var rg = gum.start()) {
			for (int i = 0; i < 30; i++) {
				rg.log(TestLogEventFactory.of().event(lineFor(i)));
			}
			rg.config().outputRegistry().flush();
		}

		assertTrue(Files.exists(dir.resolve("app.log.1")));
		assertTrue(Files.exists(dir.resolve("app.log.2")));
		assertFalse(Files.exists(dir.resolve("app.log.3")), "maxHistory=2 must evict anything older");
	}

	@Test
	void cleanHistoryOnStartPrunesLeftoverArchivesBeyondMaxHistory() throws IOException {
		Path active = dir.resolve("app.log");
		Files.writeString(dir.resolve("app.log.1"), "keep");
		Files.writeString(dir.resolve("app.log.2"), "drop");
		Files.writeString(dir.resolve("app.log.3"), "drop");
		var provider = RollingFileOutput.of(b -> {
			b.fileName(active.toString());
			b.maxHistory(1);
			b.cleanHistoryOnStart(true);
		});
		var config = LogConfig.builder().build();
		var gum = RainbowGum.builder(config).route(r -> r.appender("file", a -> a.output(provider))).build();

		try (var rg = gum.start()) {
			// starting alone must trigger cleanHistoryOnStart.
		}

		assertTrue(Files.exists(dir.resolve("app.log.1")));
		assertFalse(Files.exists(dir.resolve("app.log.2")));
		assertFalse(Files.exists(dir.resolve("app.log.3")));
	}

	@Test
	void prudentPropertyReachesInnerFileOutputAndRollsCorrectly() throws IOException {
		// prudent is not a RollingFileOutputBuilder property; it only reaches the
		// delegate FileOutput because both builders share the same
		// logging.output.{name}. prefix and DefaultRollingFileOutput.write(ByteBuffer,
		// ...) forwards straight through - this is the only way to reach that overload.
		Path active = dir.resolve("app.log");
		var props = LogProperties.builder().fromProperties("logging.output.file.prudent=true").build();
		var config = LogConfig.builder().properties(props).build();
		var provider = RollingFileOutput.of(b -> {
			b.fileName(active.toString());
			b.maxFileSize(DataSize.ofBytes(10));
			b.maxHistory(2);
		});
		var gum = RainbowGum.builder(config)
			.route(r -> r.appender("file", a -> a.output(provider).formatter(FORMATTER)))
			.build();

		try (var rg = gum.start()) {
			for (int i = 0; i < 10; i++) {
				rg.log(TestLogEventFactory.of().event(lineFor(i)));
			}
			rg.config().outputRegistry().flush();
		}

		assertTrue(Files.exists(dir.resolve("app.log.1")), "prudent mode must still roll like normal mode");
	}

	@Test
	void reopenDelegatesToUnderlyingFileOutputAfterExternalRotation() throws IOException {
		Path active = dir.resolve("app.log");
		var provider = RollingFileOutput.of(b -> {
			b.fileName(active.toString());
			b.maxFileSize(DataSize.ofBytes(1000));
		});
		var config = LogConfig.builder().build();
		var gum = RainbowGum.builder(config)
			.route(r -> r.appender("file", a -> a.output(provider).formatter(FORMATTER)))
			.build();

		try (var rg = gum.start()) {
			rg.log(TestLogEventFactory.of().event("first"));
			rg.config().outputRegistry().flush();
			Path movedAway = dir.resolve("app.log.moved");
			Files.move(active, movedAway);

			var errors = rg.config().outputRegistry().reopen();
			assertTrue(errors.isEmpty(), () -> "expected no reopen errors, got: " + errors);

			rg.log(TestLogEventFactory.of().event("second"));
			rg.config().outputRegistry().flush();
			assertEquals("second\n", Files.readString(active));
			assertEquals("first\n", Files.readString(movedAway));
		}
	}

	@Test
	void customFileNamePatternAndTotalSizeCapApplyEndToEnd() throws IOException {
		// each "lineN\n" is exactly 6 bytes; maxFileSize=5 rolls after every single
		// event, and totalSizeCap=10 (< 2 archives worth) keeps only the newest
		// archive, evicting older ones - both set via the builder lambda, not
		// properties, so this only exercises RollingFileOutputBuilder's generated
		// totalSizeCap(...)/fileNamePattern(...) setters.
		Path active = dir.resolve("app.log");
		var provider = RollingFileOutput.of(b -> {
			b.fileName(active.toString());
			b.maxFileSize(DataSize.ofBytes(5));
			b.maxHistory(7);
			b.totalSizeCap(DataSize.ofBytes(10));
			b.fileNamePattern("-%i.archive");
		});
		var config = LogConfig.builder().build();
		var gum = RainbowGum.builder(config)
			.route(r -> r.appender("file", a -> a.output(provider).formatter(FORMATTER)))
			.build();

		try (var rg = gum.start()) {
			for (int i = 0; i < 4; i++) {
				rg.log(TestLogEventFactory.of().event("line" + i));
			}
			rg.config().outputRegistry().flush();
		}

		assertEquals("line3\n", Files.readString(active));
		assertEquals("line2\n", Files.readString(dir.resolve("app.log-1.archive")),
				"custom pattern must be used for archive names");
		assertFalse(Files.exists(dir.resolve("app.log.1")), "default pattern must not be used once overridden");
		assertFalse(Files.exists(dir.resolve("app.log-2.archive")),
				"totalSizeCap set via the builder lambda must evict the older archive");
	}

	@Test
	void rollingUriSchemeWithoutQueryParamsUsesPlainConfigProperties() throws IOException {
		Path active = dir.resolve("noquery.log");
		var props = LogProperties.builder()
			.fromProperties("logging.output.file.maxFileSize=1\nlogging.output.file.maxHistory=1")
			.build();
		var config = LogConfig.builder().serviceLoader().properties(props).build();
		var uri = URI.create("rolling://" + active.toAbsolutePath());
		var ref = LogProviderRef.of(uri);

		var output = config.outputRegistry().provide(ref).provide("file", config);
		output.start(config);
		try {
			var event = TestLogEventFactory.of().event("first");
			output.write(event, "first\n");
			output.flush();
			// second write exceeds maxFileSize=1 (from plain config properties, no
			// query string), must trigger a roll before writing.
			output.write(event, "second\n");
			output.flush();
		}
		finally {
			output.close();
		}

		assertEquals("first\n", Files.readString(dir.resolve("noquery.log.1")));
		assertEquals("second\n", Files.readString(active));
	}

	@Test
	void rollingUriSchemeResolvesViaServiceLoaderAndHonorsQueryParams() throws IOException {
		Path active = dir.resolve("scheme.log");
		var config = LogConfig.builder().serviceLoader().build();
		var uri = URI.create("rolling://" + active.toAbsolutePath() + "?maxFileSize=1&maxHistory=1");
		var ref = LogProviderRef.of(uri);

		var output = config.outputRegistry().provide(ref).provide("scheme", config);
		output.start(config);
		try {
			var event = TestLogEventFactory.of().event("first");
			output.write(event, "first\n");
			output.flush();
			// second write exceeds maxFileSize=1, must trigger a roll before writing.
			output.write(event, "second\n");
			output.flush();
		}
		finally {
			output.close();
		}

		assertEquals("first\n", Files.readString(dir.resolve("scheme.log.1")));
		assertEquals("second\n", Files.readString(active));
	}

	private static String lineFor(int i) {
		return "L%03d-0123456789".formatted(i);
	}

}

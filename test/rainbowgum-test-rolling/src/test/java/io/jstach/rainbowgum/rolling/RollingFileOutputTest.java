package io.jstach.rainbowgum.rolling;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import io.jstach.rainbowgum.LogConfig;
import io.jstach.rainbowgum.LogFormatter;
import io.jstach.rainbowgum.LogProviderRef;
import io.jstach.rainbowgum.RainbowGum;
import io.jstach.rainbowgum.TestLogEventFactory;

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

	@Test
	void rollsWhenMaxFileSizeExceededAndPreservesAllEventsInOrder() throws IOException {
		Path active = dir.resolve("app.log");
		var provider = RollingFileOutput.of(b -> {
			b.fileName(active.toString());
			b.maxFileSize(50);
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
			b.maxFileSize(10);
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

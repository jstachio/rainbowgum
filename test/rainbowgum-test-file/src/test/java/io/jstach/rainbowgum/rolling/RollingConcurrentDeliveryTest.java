package io.jstach.rainbowgum.rolling;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.IOException;
import java.lang.System.Logger.Level;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.zip.GZIPInputStream;

import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import io.jstach.rainbowgum.KeyValues;
import io.jstach.rainbowgum.LogConfig;
import io.jstach.rainbowgum.LogEvent;
import io.jstach.rainbowgum.LogFormatter;
import io.jstach.rainbowgum.LogProperties;
import io.jstach.rainbowgum.LogPublisher.PublisherFactory;
import io.jstach.rainbowgum.RainbowGum;
import io.jstach.rainbowgum.file.DataSize;

class RollingConcurrentDeliveryTest {

	@TempDir
	Path directory;

	enum Mode {

		SYNC_BUFFERED, SYNC_UNBUFFERED, SYNC_PRUDENT, ASYNC_BUFFERED, ASYNC_UNBUFFERED, ASYNC_PRUDENT

	}

	@ParameterizedTest
	@EnumSource(Mode.class)
	@Timeout(20)
	void concurrentEventsSurviveRotationAndShutdown(Mode mode) throws Exception {
		for (boolean gzip : List.of(false, true)) {
			Path folder = Files.createDirectory(directory.resolve(gzip ? "gzip" : "plain"));
			Path active = folder.resolve("app.log");
			var config = LogConfig.builder().properties(LogProperties.StandardProperties.EMPTY).build();
			boolean async = mode.name().startsWith("ASYNC");
			var publisher = async ? PublisherFactory.async().bufferSize(16).build() : PublisherFactory.sync().build();
			var provider = RollingFileOutput.of(b -> b.fileName(active.toString()).maxFileSize(DataSize.ofBytes(128))
				.maxHistory(64).fileNamePattern(gzip ? ".%i.gz" : ".%i")
				.prudent(mode.name().endsWith("PRUDENT"))
				.bufferSize(mode.name().endsWith("UNBUFFERED") ? DataSize.ZERO : DataSize.ofBytes(8192)));
			try (var gum = RainbowGum.builder(config)
				.route(r -> r.publisher(publisher).appender("file",
						a -> a.output(provider).formatter(LogFormatter.builder().message().newline().build())))
				.build()
				.start()) {
				try (var executor = Executors.newFixedThreadPool(3)) {
					var jobs = new ArrayList<java.util.concurrent.Future<?>>();
					for (int thread = 0; thread < 3; thread++) {
						int producer = thread;
						jobs.add(executor.submit(() -> {
							for (int i = 0; i < 100; i++) {
								gum.log(LogEvent.of(Instant.EPOCH, "producer", producer, Level.INFO, "file",
										line(producer * 100 + i), KeyValues.of(), null));
								}
						}));
					}
					for (var job : jobs) {
						job.get(10, TimeUnit.SECONDS);
					}
				}
			}
			var text = new StringBuilder();
			for (int index = 64; index >= 1; index--) {
				Path archive = folder.resolve("app.log." + index + (gzip ? ".gz" : ""));
				if (Files.exists(archive)) {
					text.append(read(archive, gzip));
				}
			}
			text.append(Files.readString(active));
			var actual = text.toString().lines().sorted().toList();
			var expected = java.util.stream.IntStream.range(0, 300).mapToObj(RollingConcurrentDeliveryTest::line)
				.toList();
			assertEquals(expected, actual);
			assertEquals(List.of(), config.alerts().dump());
		}
	}

	private static String line(int value) {
		return String.format(Locale.ROOT, "event-%04d", value);
	}

	private static String read(Path file, boolean gzip) throws IOException {
		if (!gzip) {
			return Files.readString(file);
		}
		try (var input = new GZIPInputStream(Files.newInputStream(file))) {
			return new String(input.readAllBytes(), StandardCharsets.UTF_8);
		}
	}

}

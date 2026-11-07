package io.jstach.rainbowgum.file;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.FileOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.lang.System.Logger.Level;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import io.jstach.rainbowgum.KeyValues;
import io.jstach.rainbowgum.LogConfig;
import io.jstach.rainbowgum.LogEvent;
import io.jstach.rainbowgum.LogOutput;

class ReopenableFileOutputTest {

	@Test
	void failedReopenKeepsWritingToTheCurrentFile(@TempDir Path dir) throws IOException {
		Path file = dir.resolve("app.log");
		var opens = new AtomicInteger();
		var output = new ReopenableFileOutput(() -> {
			if (opens.incrementAndGet() > 1) {
				throw new IOException("cannot open");
			}
			return new FileOutputStreamOutput(file.toUri(), new FileOutputStream(file.toFile(), true));
		});
		output.start(LogConfig.builder().build());
		write(output, "before\n");
		assertThrows(UncheckedIOException.class, output::reopen);
		write(output, "after\n");
		output.close();
		assertEquals("before\nafter\n", Files.readString(file));
	}

	@Test
	void reopenSwitchesToTheNewFile(@TempDir Path dir) throws IOException {
		/*
		 * Each open names a different file, standing in for logrotate moving the file
		 * before signalling a reopen: Windows does not allow renaming a file that is
		 * open.
		 */
		var opens = new AtomicInteger();
		var output = new ReopenableFileOutput(() -> {
			Path file = dir.resolve("app-" + opens.incrementAndGet() + ".log");
			return new FileOutputStreamOutput(file.toUri(), new FileOutputStream(file.toFile(), true));
		});
		output.start(LogConfig.builder().build());
		write(output, "old\n");
		output.reopen();
		write(output, "new\n");
		output.close();
		assertEquals("old\n", Files.readString(dir.resolve("app-1.log")));
		assertEquals("new\n", Files.readString(dir.resolve("app-2.log")));
	}

	@Test
	void reopenDoesNotTruncateBufferedEventsWhenAppendIsFalse(@TempDir Path dir) throws IOException {
		Path file = dir.resolve("app.log");
		Files.writeString(file, "discard initial contents");
		var config = LogConfig.builder().build();
		try (var output = FileOutput.of(b -> b.fileName(file.toString()).append(false)).provide("file", config)) {
			output.start(config);
			byte[] bytes = "before\n".getBytes(StandardCharsets.UTF_8);
			output.write(LogEvent.of(Instant.EPOCH, "main", 1, Level.INFO, "test", "before", KeyValues.of(), null),
					bytes, 0, bytes.length, LogOutput.ContentType.StandardContentType.TEXT_PLAIN);
			output.reopen();
			write(output, "after\n");
			output.reopen();
			write(output, "last\n");
		}
		assertEquals("before\nafter\nlast\n", Files.readString(file));
	}

	private static void write(LogOutput output, String line) {
		byte[] bytes = line.getBytes(StandardCharsets.UTF_8);
		var event = LogEvent.of(Instant.EPOCH, "main", 1, Level.INFO, "test", line, KeyValues.of(), null);
		output.write(event, bytes, 0, bytes.length, LogOutput.ContentType.StandardContentType.TEXT_PLAIN);
		output.flush();
	}

}

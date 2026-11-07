package io.jstach.rainbowgum.file;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.IOException;
import java.lang.System.Logger.Level;
import java.nio.ByteBuffer;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.channels.ReadableByteChannel;
import java.nio.channels.WritableByteChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Instant;

import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import io.jstach.rainbowgum.KeyValues;
import io.jstach.rainbowgum.LogAppender.AppenderType;
import io.jstach.rainbowgum.LogConfig;
import io.jstach.rainbowgum.LogEvent;
import io.jstach.rainbowgum.LogFormatter;
import io.jstach.rainbowgum.LogMetrics;
import io.jstach.rainbowgum.LogOutput.ContentType.StandardContentType;
import io.jstach.rainbowgum.LogProperties;
import io.jstach.rainbowgum.RainbowGum;

class FileChannelPartialWriteTest {

	@TempDir
	Path directory;

	@ParameterizedTest
	@ValueSource(ints = { 1, 3, 64 })
	void shortWritesPreserveTheEntireEvent(int maximum) throws IOException {
		Path path = directory.resolve("events.log");
		byte[] bytes = "event\n".repeat(32).getBytes(java.nio.charset.StandardCharsets.UTF_8);
		var buffer = ByteBuffer.wrap(bytes);
		try (var output = new FileChannelOutput(path.toUri(), new ShortChannel(path, maximum))) {
			output.write(event(), buffer, StandardContentType.TEXT_PLAIN);
			assertEquals(0, buffer.remaining());
		}
		assertEquals(new String(bytes, java.nio.charset.StandardCharsets.UTF_8), Files.readString(path));
	}

	@Test
	void transientZeroProgressDoesNotLoseTheEvent() throws IOException {
		Path path = directory.resolve("events.log");
		var channel = new ShortChannel(path, 3);
		channel.zeroWrites = 2;
		try (var output = new FileChannelOutput(path.toUri(), channel)) {
			output.write(event(), "event\n");
		}
		assertEquals("event\n", Files.readString(path));
	}

	@Test
	void zeroProgressRaisesAnAlertAndCountsTheFailedEvent() throws IOException {
		Path path = directory.resolve("events.log");
		var output = new FileChannelOutput(path.toUri(), new ShortChannel(path, 0));
		var config = LogConfig.builder().properties(LogProperties.StandardProperties.EMPTY).build();
		try (var gum = RainbowGum.builder(config)
			.route(r -> r.appender("file",
					a -> a.output(output)
						.formatter(LogFormatter.builder().message().newline().build())
						.appenderType(AppenderType.REUSE_BUFFER)))
			.build()
			.start()) {
			gum.log(event());
			assertEquals(1, config.alerts().dump().size());
			var alert = config.alerts().dump().getFirst();
			assertEquals(Level.ERROR, alert.level());
			assertEquals("appender 'file' failed to append event", alert.message());
			var failure = java.util.Objects.requireNonNull(alert.throwableOrNull());
			assertEquals("File channel made no progress writing to '" + path.toUri() + "'",
					java.util.Objects.requireNonNull(failure.getCause()).getMessage());
			assertEquals(1,
					config.metrics()
						.snapshot()
						.stream()
						.filter(c -> c.name().equals(LogMetrics.EVENTS_FAILED_METRIC))
						.mapToLong(c -> c.value())
						.sum());
		}
	}

	private static LogEvent event() {
		return LogEvent.of(Instant.EPOCH, "main", 1, Level.INFO, "file", "event", KeyValues.of(), null);
	}

	private static final class ShortChannel extends FileChannel {

		private final FileChannel delegate;

		private final int maximum;

		int zeroWrites;

		ShortChannel(Path path, int maximum) throws IOException {
			this.delegate = FileChannel.open(path, StandardOpenOption.CREATE, StandardOpenOption.WRITE,
					StandardOpenOption.READ);
			this.maximum = maximum;
		}

		@Override
		public int write(ByteBuffer source) throws IOException {
			if (zeroWrites > 0) {
				zeroWrites--;
				return 0;
			}
			int limit = source.limit();
			try {
				source.limit(source.position() + Math.min(source.remaining(), maximum));
				return delegate.write(source);
			}
			finally {
				source.limit(limit);
			}
		}

		@Override
		public int read(ByteBuffer dst) throws IOException {
			return delegate.read(dst);
		}

		@Override
		public long read(ByteBuffer[] dsts, int off, int len) throws IOException {
			return delegate.read(dsts, off, len);
		}

		@Override
		public long write(ByteBuffer[] srcs, int off, int len) throws IOException {
			return delegate.write(srcs, off, len);
		}

		@Override
		public long position() throws IOException {
			return delegate.position();
		}

		@Override
		public FileChannel position(long value) throws IOException {
			delegate.position(value);
			return this;
		}

		@Override
		public long size() throws IOException {
			return delegate.size();
		}

		@Override
		public FileChannel truncate(long value) throws IOException {
			delegate.truncate(value);
			return this;
		}

		@Override
		public void force(boolean metadata) throws IOException {
			delegate.force(metadata);
		}

		@Override
		public long transferTo(long pos, long count, WritableByteChannel target) throws IOException {
			return delegate.transferTo(pos, count, target);
		}

		@Override
		public long transferFrom(ReadableByteChannel src, long pos, long count) throws IOException {
			return delegate.transferFrom(src, pos, count);
		}

		@Override
		public int read(ByteBuffer dst, long pos) throws IOException {
			return delegate.read(dst, pos);
		}

		@Override
		public int write(ByteBuffer src, long pos) throws IOException {
			return delegate.write(src, pos);
		}

		@Override
		public MappedByteBuffer map(MapMode mode, long pos, long size) throws IOException {
			return delegate.map(mode, pos, size);
		}

		@Override
		public FileLock lock(long pos, long size, boolean shared) throws IOException {
			return delegate.lock(pos, size, shared);
		}

		@Override
		public @Nullable FileLock tryLock(long pos, long size, boolean shared) throws IOException {
			return delegate.tryLock(pos, size, shared);
		}

		@Override
		protected void implCloseChannel() throws IOException {
			delegate.close();
		}

	}

}

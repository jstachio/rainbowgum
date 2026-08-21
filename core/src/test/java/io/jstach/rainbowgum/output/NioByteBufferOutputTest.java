package io.jstach.rainbowgum.output;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URI;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

import org.junit.jupiter.api.Test;

import io.jstach.rainbowgum.LogConfig;
import io.jstach.rainbowgum.LogEncoder;
import io.jstach.rainbowgum.LogEncoder.Buffer;
import io.jstach.rainbowgum.LogEncoder.BufferHints;
import io.jstach.rainbowgum.LogEvent;
import io.jstach.rainbowgum.LogOutput;
import io.jstach.rainbowgum.RainbowGum;
import io.jstach.rainbowgum.TestEventBuilder;

/*
 * No output in the codebase today actually advertises WriteMethod.BYTE_BUFFER and relies
 * on LogOutput's default write(LogEvent, ByteBuffer, ContentType) - FileChannelOutput
 * (the only BYTE_BUFFER-hinting output) overrides that method itself and never goes
 * through the default. This constructs a real NIO FileChannel output plus a real
 * ByteBuffer-backed encoder Buffer, driven through a full RainbowGum load (not a mock),
 * to check that default method against the standard java.nio idiom: fill the buffer,
 * flip() it (or hand over one built with ByteBuffer.wrap(byte[])), and let the reader
 * consume from position to limit. The default used to size its array off buf.position()
 * instead of buf.remaining(), which only worked for a non-standard "never flipped,
 * position tracks written length" convention and silently wrote nothing for a properly
 * flipped buffer - see git history on this file for that finding. Fixed to use
 * buf.remaining()/buf.get(arr), so these now assert the standard idiom actually works.
 */
class NioByteBufferOutputTest {

	@Test
	void flippedBufferConventionWritesFullContent(@org.junit.jupiter.api.io.TempDir Path dir) throws IOException {
		Path file = dir.resolve("flipped.log");
		var output = new NioFileOutput(file);
		var config = LogConfig.builder().build();
		var gum = RainbowGum.builder(config).route(r -> {
			r.appender("file", a -> {
				a.output(output);
				a.encoder(new ByteBufferEncoder());
			});
		}).build();
		try (var g = gum.start()) {
			g.log(TestEventBuilder.of().build(b -> b.message("hello world")));
			output.flush();
			String actual = Files.readString(file, StandardCharsets.UTF_8);
			assertEquals("hello world\n", actual);
		}
	}

	@Test
	void wrappedBufferAlsoWritesFullContent(@org.junit.jupiter.api.io.TempDir Path dir) throws IOException {
		Path file = dir.resolve("wrapped.log");
		try (var output = new NioFileOutput(file)) {
			var event = TestEventBuilder.of().build(b -> b.message("hello world"));
			var buf = ByteBuffer.wrap("hello world\n".getBytes(StandardCharsets.UTF_8));
			output.write(event, buf, LogOutput.ContentType.StandardContentType.TEXT_PLAIN);
			output.flush();
			String actual = Files.readString(file, StandardCharsets.UTF_8);
			assertEquals("hello world\n", actual);
		}
	}

	static final class ByteBufferBuffer implements Buffer {

		final ByteBuffer byteBuffer = ByteBuffer.allocate(8192);

		@Override
		public void drain(LogOutput output, LogEvent event) {
			byteBuffer.flip();
			output.write(event, byteBuffer, LogOutput.ContentType.StandardContentType.TEXT_PLAIN);
		}

		@Override
		public void clear() {
			byteBuffer.clear();
		}

	}

	static final class ByteBufferEncoder implements LogEncoder {

		@Override
		public Buffer buffer(BufferHints hints) {
			return new ByteBufferBuffer();
		}

		@Override
		public void encode(LogEvent event, Buffer buffer) {
			var b = (ByteBufferBuffer) buffer;
			b.clear();
			StringBuilder sb = new StringBuilder();
			event.formattedMessage(sb);
			sb.append('\n');
			b.byteBuffer.put(sb.toString().getBytes(StandardCharsets.UTF_8));
		}

	}

	static final class NioFileOutput implements LogOutput {

		private final FileChannel channel;

		private final URI uri;

		NioFileOutput(Path path) throws IOException {
			this.uri = path.toUri();
			this.channel = FileChannel.open(path, StandardOpenOption.CREATE, StandardOpenOption.WRITE,
					StandardOpenOption.TRUNCATE_EXISTING);
		}

		@Override
		public URI uri() {
			return uri;
		}

		@Override
		public OutputType type() {
			return OutputType.FILE;
		}

		@Override
		public BufferHints bufferHints() {
			return WriteMethod.BYTE_BUFFER;
		}

		@Override
		public void write(LogEvent event, byte[] bytes, int off, int len, ContentType contentType) {
			try {
				channel.write(ByteBuffer.wrap(bytes, off, len));
			}
			catch (IOException e) {
				throw new UncheckedIOException(e);
			}
		}

		@Override
		public void flush() {
			try {
				channel.force(false);
			}
			catch (IOException e) {
				throw new UncheckedIOException(e);
			}
		}

		@Override
		public void close() {
			try {
				channel.close();
			}
			catch (IOException e) {
				throw new UncheckedIOException(e);
			}
		}

	}

}

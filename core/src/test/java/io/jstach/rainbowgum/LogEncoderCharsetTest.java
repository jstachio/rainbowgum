package io.jstach.rainbowgum;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.net.URI;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import io.jstach.rainbowgum.LogOutput.ContentType;
import io.jstach.rainbowgum.LogOutput.WriteMethod;

/*
 * LogEncoder.builder(LogFormatter).charset(...)/.contentType(...) - verified with raw
 * captured bytes rather than ListLogOutput (whose own write(byte[]...) overload always
 * decodes as UTF-8 for storage, which would defeat a test trying to confirm a
 * *different* charset was used).
 */
class LogEncoderCharsetTest {

	private static final LogFormatter FORMATTER = LogFormatter.builder().message().build();

	// 'é' is 2 bytes in UTF-8 (0xC3 0xA9) but 1 byte in ISO-8859-1 (0xE9) - a message
	// containing it encodes to genuinely different bytes under the two charsets, so
	// matching bytes confirm the requested charset was actually used, not just assumed.
	private static final String MESSAGE = "café";

	@Test
	void bytesWriteMethodUsesRequestedCharset() {
		var output = new CapturingOutput(WriteMethod.BYTES);
		encodeInto(output, StandardCharsets.ISO_8859_1);
		assertArrayEquals(MESSAGE.getBytes(StandardCharsets.ISO_8859_1), output.capturedBytes());
		assertEquals(StandardCharsets.ISO_8859_1, output.capturedContentType().charsetOrNull());
	}

	@Test
	void byteBufferWriteMethodUsesRequestedCharset() {
		var output = new CapturingOutput(WriteMethod.BYTE_BUFFER);
		encodeInto(output, StandardCharsets.ISO_8859_1);
		assertArrayEquals(MESSAGE.getBytes(StandardCharsets.ISO_8859_1), output.capturedBytes());
		assertEquals(StandardCharsets.ISO_8859_1, output.capturedContentType().charsetOrNull());
	}

	@Test
	void defaultOverloadStillEncodesUtf8() {
		var output = new CapturingOutput(WriteMethod.BYTES);
		var config = LogConfig.builder().build();
		var gum = RainbowGum.builder(config)
			.route(r -> r.appender("list", a -> a.output(output).encoder(LogEncoder.of(FORMATTER))))
			.build();
		try (var g = gum.start()) {
			g.log(event());
		}
		assertArrayEquals(MESSAGE.getBytes(StandardCharsets.UTF_8), output.capturedBytes());
		assertEquals(StandardCharsets.UTF_8, output.capturedContentType().charsetOrNull());
	}

	@Test
	void ofContentTypeUsesTheGivenContentTypeAndItsCharset() {
		var output = new CapturingOutput(WriteMethod.BYTES);
		var contentType = new SimpleContentType("text/csv", StandardCharsets.ISO_8859_1);
		var config = LogConfig.builder().build();
		var gum = RainbowGum.builder(config)
			.route(r -> r.appender("list",
					a -> a.output(output).encoder(LogEncoder.builder(FORMATTER).contentType(contentType).build())))
			.build();
		try (var g = gum.start()) {
			g.log(event());
		}
		assertArrayEquals(MESSAGE.getBytes(StandardCharsets.ISO_8859_1), output.capturedBytes());
		assertEquals(contentType, output.capturedContentType());
	}

	@Test
	void ofContentTypeWithNoCharsetDefaultsToUtf8ForEncoding() {
		var output = new CapturingOutput(WriteMethod.BYTES);
		var contentType = new SimpleContentType("text/csv", null);
		var config = LogConfig.builder().build();
		var gum = RainbowGum.builder(config)
			.route(r -> r.appender("list",
					a -> a.output(output).encoder(LogEncoder.builder(FORMATTER).contentType(contentType).build())))
			.build();
		try (var g = gum.start()) {
			g.log(event());
		}
		assertArrayEquals(MESSAGE.getBytes(StandardCharsets.UTF_8), output.capturedBytes());
		assertEquals(contentType, output.capturedContentType());
	}

	@Test
	void differentCharsetsProduceDifferentBytesForTheSameMessage() {
		var utf8 = new CapturingOutput(WriteMethod.BYTES);
		encodeInto(utf8, StandardCharsets.UTF_8);
		var latin1 = new CapturingOutput(WriteMethod.BYTES);
		encodeInto(latin1, StandardCharsets.ISO_8859_1);
		assertEquals(false, java.util.Arrays.equals(utf8.capturedBytes(), latin1.capturedBytes()),
				"UTF-8 and ISO-8859-1 must produce different bytes for a message containing 'é'");
	}

	private static void encodeInto(CapturingOutput output, java.nio.charset.Charset charset) {
		var config = LogConfig.builder().build();
		var gum = RainbowGum.builder(config)
			.route(r -> r.appender("list",
					a -> a.output(output).encoder(LogEncoder.builder(FORMATTER).charset(charset).build())))
			.build();
		try (var g = gum.start()) {
			g.log(event());
		}
	}

	private static LogEvent event() {
		return LogEvent.of(System.Logger.Level.INFO, "test", MESSAGE, KeyValues.of(), null);
	}

	static class CapturingOutput implements LogOutput {

		private final WriteMethod writeMethod;

		private final List<byte[]> writes = new ArrayList<>();

		private final List<ContentType> contentTypes = new ArrayList<>();

		CapturingOutput(WriteMethod writeMethod) {
			this.writeMethod = writeMethod;
		}

		byte[] capturedBytes() {
			return writes.get(writes.size() - 1);
		}

		ContentType capturedContentType() {
			return contentTypes.get(contentTypes.size() - 1);
		}

		@Override
		public LogEncoder.BufferHints bufferHints() {
			return writeMethod;
		}

		@Override
		public URI uri() {
			throw new UnsupportedOperationException();
		}

		@Override
		public OutputType type() {
			return OutputType.MEMORY;
		}

		@Override
		public void write(LogEvent event, byte[] bytes, int off, int len, ContentType contentType) {
			writes.add(java.util.Arrays.copyOfRange(bytes, off, off + len));
			contentTypes.add(contentType);
		}

		@Override
		public void write(LogEvent event, ByteBuffer buf, ContentType contentType) {
			byte[] arr = new byte[buf.remaining()];
			buf.get(arr);
			writes.add(arr);
			contentTypes.add(contentType);
		}

		@Override
		public void flush() {
		}

		@Override
		public void close() {
		}

	}

}

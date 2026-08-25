package io.jstach.rainbowgum;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.fail;

import java.nio.ByteBuffer;
import java.util.List;

import org.junit.jupiter.api.Test;

import io.jstach.rainbowgum.LogAppender.AppenderFlag;
import io.jstach.rainbowgum.LogEncoder.Buffer.DirectByteBufferBuffer;
import io.jstach.rainbowgum.LogOutput.ContentType;
import io.jstach.rainbowgum.LogOutput.WriteMethod;
import io.jstach.rainbowgum.output.ListLogOutput;

/*
 * Real, full RainbowGum loads writing to a real ListLogOutput subclass whose declared
 * WriteMethod is swapped per test - no mocks. Confirms FormatterEncoder's dispatch (added
 * when the former rainbowgum-nio module's DirectByteBufferEncoder/DirectByteBufferBuffer
 * were folded into core as the default encoder/buffer for every WriteMethod, not just an
 * opt-in) produces byte-for-byte identical output across STRING/BYTES/BYTE_BUFFER, and
 * that each write method actually reaches the LogOutput overload it is supposed to -
 * BYTES and BYTE_BUFFER must not fall through to each other's default bridging method,
 * which would defeat the point (an extra byte[] copy).
 */
class FormatterEncoderTest {

	private static final LogFormatter FORMATTER = LogFormatter.builder()
		.text("[")
		.level()
		.text("] ")
		.message()
		.newline()
		.build();

	@Test
	void asciiMessageMatchesAcrossWriteMethods() {
		assertEncodesSameAcrossWriteMethods("hello world");
	}

	@Test
	void multiByteUtf8MessageMatchesAcrossWriteMethods() {
		// accented characters, CJK, and an emoji outside the BMP (surrogate pair).
		assertEncodesSameAcrossWriteMethods("héllo wörld 你好 😀");
	}

	@Test
	void messageLargerThanInitialCapacityForcesGrowthAndStillMatches() {
		String longMessage = "x".repeat(DirectByteBufferBuffer.DEFAULT_INITIAL_BYTE_CAPACITY * 3);
		assertEncodesSameAcrossWriteMethods(longMessage);
	}

	@Test
	void reuseBufferAcrossEventsDoesNotLeakPreviousLongerMessage() {
		var output = new WriteMethodOutput(WriteMethod.BYTE_BUFFER);
		encodeInto(output, List.of(AppenderFlag.REUSE_BUFFER),
				"this is a much longer first message that should not leak", "short");
		assertEquals(List.of("[INFO] this is a much longer first message that should not leak\n", "[INFO] short\n"),
				output.events().stream().map(e -> e.getValue()).toList());
	}

	@Test
	void byteBufferWriteMethodCallsByteBufferOverloadDirectly() {
		var output = new WriteMethodOutput(WriteMethod.BYTE_BUFFER) {
			@Override
			public void write(LogEvent event, byte[] bytes, int off, int len, ContentType contentType) {
				fail("expected write(LogEvent, ByteBuffer, ContentType) to be called directly for BYTE_BUFFER, "
						+ "not the byte[] overload");
			}

			@Override
			public void write(LogEvent event, ByteBuffer buf, ContentType contentType) {
				byte[] arr = new byte[buf.remaining()];
				buf.get(arr);
				write(event, new String(arr, java.nio.charset.StandardCharsets.UTF_8));
			}
		};
		encodeInto(output, List.of(), "hello");
		assertEquals(List.of("[INFO] hello\n"), output.events().stream().map(e -> e.getValue()).toList());
	}

	@Test
	void bytesWriteMethodCallsByteArrayOverloadDirectlyNotTheByteBufferBridge() {
		var output = new WriteMethodOutput(WriteMethod.BYTES) {
			@Override
			public void write(LogEvent event, ByteBuffer buf, ContentType contentType) {
				fail("expected the byte[] overload to be called directly for BYTES, "
						+ "not LogOutput's default ByteBuffer bridge");
			}
		};
		encodeInto(output, List.of(), "hello");
		assertEquals(List.of("[INFO] hello\n"), output.events().stream().map(e -> e.getValue()).toList());
	}

	@Test
	void stringWriteMethodUsesStringBuilderBufferNotAByteBasedOverload() {
		var output = new WriteMethodOutput(WriteMethod.STRING) {
			@Override
			public void write(LogEvent event, byte[] bytes, int off, int len, ContentType contentType) {
				fail("expected write(LogEvent, String) to be called for STRING, not the byte[] overload");
			}

			@Override
			public void write(LogEvent event, ByteBuffer buf, ContentType contentType) {
				fail("expected write(LogEvent, String) to be called for STRING, not the ByteBuffer overload");
			}
		};
		encodeInto(output, List.of(), "hello");
		assertEquals(List.of("[INFO] hello\n"), output.events().stream().map(e -> e.getValue()).toList());
	}

	private static void assertEncodesSameAcrossWriteMethods(String message) {
		String string = encodeWith(WriteMethod.STRING, message);
		String bytes = encodeWith(WriteMethod.BYTES, message);
		String byteBuffer = encodeWith(WriteMethod.BYTE_BUFFER, message);
		assertEquals(string, bytes);
		assertEquals(string, byteBuffer);
	}

	private static String encodeWith(WriteMethod writeMethod, String message) {
		var output = new WriteMethodOutput(writeMethod);
		encodeInto(output, List.of(), message);
		return output.toString();
	}

	private static void encodeInto(WriteMethodOutput output, List<AppenderFlag> flags, String... messages) {
		var config = LogConfig.builder().build();
		var gum = RainbowGum.builder(config).route(r -> {
			r.appender("list", a -> {
				a.output(output);
				a.encoder(LogEncoder.of(FORMATTER));
				a.flags(flags);
			});
		}).build();
		try (var g = gum.start()) {
			for (var message : messages) {
				g.log(event(message));
			}
		}
	}

	private static LogEvent event(String message) {
		return LogEvent.of(System.Logger.Level.INFO, "test", message, KeyValues.of(), null);
	}

	static class WriteMethodOutput extends ListLogOutput {

		private final WriteMethod writeMethod;

		WriteMethodOutput(WriteMethod writeMethod) {
			this.writeMethod = writeMethod;
		}

		@Override
		public LogEncoder.BufferHints bufferHints() {
			return writeMethod;
		}

	}

}

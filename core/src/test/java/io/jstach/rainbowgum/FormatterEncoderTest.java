package io.jstach.rainbowgum;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.List;

import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;

import io.jstach.rainbowgum.LogAppender.AppenderType;
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
		encodeInto(output, AppenderType.REUSE_BUFFER, "this is a much longer first message that should not leak",
				"short");
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
		encodeInto(output, null, "hello");
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
		encodeInto(output, null, "hello");
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
		encodeInto(output, null, "hello");
		assertEquals(List.of("[INFO] hello\n"), output.events().stream().map(e -> e.getValue()).toList());
	}

	@Test
	void useGetBytesEncodesSameAsDefaultCharsetEncoderPath() {
		String ascii = encodeWith(WriteMethod.BYTES, "hello world", false);
		String asciiGetBytes = encodeWith(WriteMethod.BYTES, "hello world", true);
		assertEquals(ascii, asciiGetBytes);

		// accented characters, CJK, and an emoji outside the BMP (surrogate pair) -
		// exercises getBytes()'s compact-strings fallback path, not just the Latin1
		// fast path.
		String multiByte = encodeWith(WriteMethod.BYTES, "héllo wörld 你好 😀", false);
		String multiByteGetBytes = encodeWith(WriteMethod.BYTES, "héllo wörld 你好 😀", true);
		assertEquals(multiByte, multiByteGetBytes);
	}

	@Test
	void useGetBytesCallsByteArrayOverloadDirectlyForBytesHint() {
		boolean[] byteArrayOverloadCalled = { false };
		var output = new WriteMethodOutput(WriteMethod.BYTES) {
			@Override
			public void write(LogEvent event, byte[] bytes, int off, int len, ContentType contentType) {
				byteArrayOverloadCalled[0] = true;
				super.write(event, bytes, off, len, contentType);
			}

			@Override
			public void write(LogEvent event, ByteBuffer buf, ContentType contentType) {
				fail("expected the byte[] overload to be called directly, not the ByteBuffer bridge");
			}
		};
		encodeInto(output, null, true, "hello");
		assertTrue(byteArrayOverloadCalled[0], "expected the byte[] overload to be called with useGetBytes(true)");
		assertEquals(List.of("[INFO] hello\n"), output.events().stream().map(e -> e.getValue()).toList());
	}

	/*
	 * Documents a real, deliberate consequence of useGetBytes(true) noted on its own
	 * javadoc: StringBuilderBufferBytes always calls the byte[] overload, so it overrides
	 * an output's own BYTE_BUFFER preference rather than combining with it - this is not
	 * a bug, just something worth locking in with a test so a future change to that
	 * behavior is a deliberate decision, not an accident.
	 */
	@Test
	void useGetBytesOverridesByteBufferHintWithByteArrayOverload() {
		boolean[] byteArrayOverloadCalled = { false };
		var output = new WriteMethodOutput(WriteMethod.BYTE_BUFFER) {
			@Override
			public void write(LogEvent event, byte[] bytes, int off, int len, ContentType contentType) {
				byteArrayOverloadCalled[0] = true;
				super.write(event, bytes, off, len, contentType);
			}
		};
		encodeInto(output, null, true, "hello");
		assertTrue(byteArrayOverloadCalled[0],
				"useGetBytes(true) should call the byte[] overload even for a BYTE_BUFFER-hinting output");
		assertEquals(List.of("[INFO] hello\n"), output.events().stream().map(e -> e.getValue()).toList());
	}

	@Test
	void useGetBytesBufferGrowsAndShrinksLikeItsSiblingBuffers() {
		var config = LogConfig.builder().build();
		LogEncoder encoder = LogEncoder.builder(FORMATTER)
			.charset(StandardCharsets.UTF_8)
			.maxBufferSize(10_000)
			.useGetBytes(true)
			.build()
			.provide("test", config);
		var buffer = (StringBuilderBufferBytes) encoder.buffer(WriteMethod.BYTES);

		encoder.encode(event("x".repeat(20_000)), buffer);
		int grownCapacity = buffer.stringBuilder.capacity();
		assertTrue(grownCapacity > 10_000, "sanity check: the big message must have actually grown the buffer");

		buffer.clear();

		assertTrue(buffer.stringBuilder.capacity() < grownCapacity,
				"clear() must shrink the backing StringBuilder back down once oversized");
	}

	private static void assertEncodesSameAcrossWriteMethods(String message) {
		String string = encodeWith(WriteMethod.STRING, message);
		String bytes = encodeWith(WriteMethod.BYTES, message);
		String byteBuffer = encodeWith(WriteMethod.BYTE_BUFFER, message);
		assertEquals(string, bytes);
		assertEquals(string, byteBuffer);
	}

	private static String encodeWith(WriteMethod writeMethod, String message) {
		return encodeWith(writeMethod, message, false);
	}

	private static String encodeWith(WriteMethod writeMethod, String message, boolean useGetBytes) {
		var output = new WriteMethodOutput(writeMethod);
		encodeInto(output, null, useGetBytes, message);
		return output.toString();
	}

	private static void encodeInto(WriteMethodOutput output, @Nullable AppenderType type, String... messages) {
		encodeInto(output, type, false, messages);
	}

	private static void encodeInto(WriteMethodOutput output, @Nullable AppenderType type, boolean useGetBytes,
			String... messages) {
		var config = LogConfig.builder().build();
		var gum = RainbowGum.builder(config).route(r -> {
			r.appender("list", a -> {
				a.output(output);
				a.encoder(LogEncoder.builder(FORMATTER).useGetBytes(useGetBytes).build());
				if (type != null) {
					a.appenderType(type);
				}
			});
		}).build();
		try (var g = gum.start()) {
			for (var message : messages) {
				g.log(event(message));
			}
		}
	}

	private static LogEvent event(String message) {
		return TestLogEventFactory.of("test")
			.eventNoArg(System.Logger.Level.INFO, message, KeyValues.of(), (Throwable) null);
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

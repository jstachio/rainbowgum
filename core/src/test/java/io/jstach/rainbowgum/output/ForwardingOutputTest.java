package io.jstach.rainbowgum.output;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.URI;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.List;

import org.eclipse.jdt.annotation.Nullable;
import org.junit.jupiter.api.Test;

import io.jstach.rainbowgum.LogConfig;
import io.jstach.rainbowgum.LogEncoder.Buffer.StringBuilderBuffer;
import io.jstach.rainbowgum.LogEncoder.BufferHints;
import io.jstach.rainbowgum.LogEvent;
import io.jstach.rainbowgum.LogOutput;
import io.jstach.rainbowgum.LogResponse.Status;
import io.jstach.rainbowgum.TestEventBuilder;

/*
 * LogOutput is protected from overlapping write/flush/close calls by the appender and
 * publisher above it (see LogOutput's own javadoc), so these tests exercise the two
 * forwarding branches (delegate present/absent) sequentially rather than concurrently.
 */
class ForwardingOutputTest {

	@Test
	void startForwardsWhenDelegatePresentAndNoopsWhenAbsent() {
		var delegate = new RecordingListLogOutput();
		var config = LogConfig.builder().build();

		assertDoesNotThrow(() -> new TestForwardingOutput(null).start(config));

		new TestForwardingOutput(delegate).start(config);
		assertEquals(1, delegate.startCount);
	}

	@Test
	void flushForwardsWhenDelegatePresentAndNoopsWhenAbsent() {
		var delegate = new RecordingListLogOutput();

		assertDoesNotThrow(() -> new TestForwardingOutput(null).flush());

		new TestForwardingOutput(delegate).flush();
		assertEquals(1, delegate.flushCount);
	}

	@Test
	void closeForwardsWhenDelegatePresentAndNoopsWhenAbsent() {
		var delegate = new RecordingListLogOutput();

		assertDoesNotThrow(() -> new TestForwardingOutput(null).close());

		new TestForwardingOutput(delegate).close();
		assertEquals(1, delegate.closeCount);
	}

	@Test
	void writeBatchForwardsWhenDelegatePresentAndNoopsWhenAbsent() {
		var delegate = new RecordingListLogOutput();
		var event = TestEventBuilder.of().build(b -> b.message("hello"));
		var encoder = io.jstach.rainbowgum.LogEncoder.of(io.jstach.rainbowgum.LogFormatter.builder().message().build());

		assertDoesNotThrow(() -> new TestForwardingOutput(null).write(new LogEvent[] { event }, 1, encoder));
		assertTrue(delegate.events().isEmpty());

		new TestForwardingOutput(delegate).write(new LogEvent[] { event }, 1, encoder);
		assertEquals(1, delegate.events().size());
	}

	@Test
	void writeBatchWithBufferForwardsWhenDelegatePresentAndNoopsWhenAbsent() {
		var delegate = new RecordingListLogOutput();
		var event = TestEventBuilder.of().build(b -> b.message("hello"));
		var encoder = io.jstach.rainbowgum.LogEncoder.of(io.jstach.rainbowgum.LogFormatter.builder().message().build());
		var buffer = encoder.buffer(delegate.bufferHints());

		assertDoesNotThrow(() -> new TestForwardingOutput(null).write(new LogEvent[] { event }, 1, encoder, buffer));
		assertTrue(delegate.events().isEmpty());

		new TestForwardingOutput(delegate).write(new LogEvent[] { event }, 1, encoder, buffer);
		assertEquals(1, delegate.events().size());
	}

	@Test
	void writeBufferForwardsWhenDelegatePresentAndNoopsWhenAbsent() {
		var delegate = new RecordingListLogOutput();
		var event = TestEventBuilder.of().build(b -> b.message("hello"));
		var buffer = StringBuilderBuffer.of(new StringBuilder("hello"));

		assertDoesNotThrow(() -> new TestForwardingOutput(null).write(event, buffer));
		assertTrue(delegate.events().isEmpty());

		new TestForwardingOutput(delegate).write(event, buffer);
		assertEquals(List.of("hello"), delegate.events().stream().map(e -> e.getValue()).toList());
	}

	@Test
	void writeStringForwardsWhenDelegatePresentAndNoopsWhenAbsent() {
		var delegate = new RecordingListLogOutput();
		var event = TestEventBuilder.of().build(b -> b.message("hello"));

		assertDoesNotThrow(() -> new TestForwardingOutput(null).write(event, "hello"));
		assertTrue(delegate.events().isEmpty());

		new TestForwardingOutput(delegate).write(event, "hello");
		assertEquals(List.of("hello"), delegate.events().stream().map(e -> e.getValue()).toList());
	}

	@Test
	void writeBytesForwardsWhenDelegatePresentAndNoopsWhenAbsent() {
		var delegate = new RecordingListLogOutput();
		var event = TestEventBuilder.of().build(b -> b.message("hello"));
		byte[] bytes = "hello".getBytes(StandardCharsets.UTF_8);

		assertDoesNotThrow(() -> new TestForwardingOutput(null).write(event, bytes,
				LogOutput.ContentType.StandardContentType.TEXT_PLAIN));
		assertTrue(delegate.events().isEmpty());

		new TestForwardingOutput(delegate).write(event, bytes, LogOutput.ContentType.StandardContentType.TEXT_PLAIN);
		assertEquals(List.of("hello"), delegate.events().stream().map(e -> e.getValue()).toList());
	}

	@Test
	void writeBytesOffsetLengthForwardsWhenDelegatePresentAndNoopsWhenAbsent() {
		var delegate = new RecordingListLogOutput();
		var event = TestEventBuilder.of().build(b -> b.message("hello"));
		byte[] bytes = "__hello__".getBytes(StandardCharsets.UTF_8);

		assertDoesNotThrow(() -> new TestForwardingOutput(null).write(event, bytes, 2, 5,
				LogOutput.ContentType.StandardContentType.TEXT_PLAIN));
		assertTrue(delegate.events().isEmpty());

		new TestForwardingOutput(delegate).write(event, bytes, 2, 5,
				LogOutput.ContentType.StandardContentType.TEXT_PLAIN);
		assertEquals(List.of("hello"), delegate.events().stream().map(e -> e.getValue()).toList());
	}

	@Test
	void writeByteBufferForwardsWhenDelegatePresentAndNoopsWhenAbsent() {
		var delegate = new RecordingListLogOutput();
		var event = TestEventBuilder.of().build(b -> b.message("hello"));

		/*
		 * LogOutput's default write(LogEvent, ByteBuffer, ContentType) sizes the array
		 * off buf.position() (expecting a just-written-into, not-yet-flipped buffer)
		 * rather than buf.remaining(), so the buffer must be built with put(), not
		 * wrap().
		 */
		assertDoesNotThrow(() -> new TestForwardingOutput(null).write(event, freshByteBuffer(),
				LogOutput.ContentType.StandardContentType.TEXT_PLAIN));
		assertTrue(delegate.events().isEmpty());

		new TestForwardingOutput(delegate).write(event, freshByteBuffer(),
				LogOutput.ContentType.StandardContentType.TEXT_PLAIN);
		assertEquals(List.of("hello"), delegate.events().stream().map(e -> e.getValue()).toList());
	}

	private static ByteBuffer freshByteBuffer() {
		ByteBuffer buf = ByteBuffer.allocate(5);
		buf.put("hello".getBytes(StandardCharsets.UTF_8));
		return buf;
	}

	static class TestForwardingOutput implements ForwardingOutput {

		private final @Nullable LogOutput delegate;

		TestForwardingOutput(@Nullable LogOutput delegate) {
			this.delegate = delegate;
		}

		@Override
		public @Nullable LogOutput delegate() {
			return delegate;
		}

		@Override
		public URI uri() {
			return URI.create("test:///forwarding");
		}

		@Override
		public OutputType type() {
			return OutputType.MEMORY;
		}

		@Override
		public BufferHints bufferHints() {
			return WriteMethod.STRING;
		}

		@Override
		public Status reopen() {
			return Status.StandardStatus.OK;
		}

	}

	static class RecordingListLogOutput extends ListLogOutput {

		int startCount = 0;

		int flushCount = 0;

		int closeCount = 0;

		@Override
		public void start(LogConfig config) {
			startCount++;
		}

		@Override
		public void flush() {
			flushCount++;
			super.flush();
		}

		@Override
		public void close() {
			closeCount++;
			super.close();
		}

	}

}

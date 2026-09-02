package io.jstach.rainbowgum;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.URI;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.concurrent.locks.ReentrantLock;

import org.junit.jupiter.api.Test;

import io.jstach.rainbowgum.LogAppender.AppenderFlag;
import io.jstach.rainbowgum.LogEncoder.Buffer.DirectByteBufferBuffer;
import io.jstach.rainbowgum.LogEncoder.Buffer.StringBuilderBuffer;
import io.jstach.rainbowgum.LogOutput.ContentType;
import io.jstach.rainbowgum.LogOutput.WriteMethod;

/*
 * Buffer#clear() self-shrinking (see LogEncoder.Buffer#isOversized()). The earlier
 * appender-level replacement mechanism from this exploration (an appender discarding an
 * oversized ThreadLocal-cached Buffer and asking the encoder for a fresh one) was deleted
 * in favor of this: clear() itself - called once per event by every appender's write path,
 * including the default batch write(LogEvent[], int, LogEncoder, Buffer) loop in
 * LogOutput.java - shrinks a buffer's own backing storage back down once it has grown
 * past its configured maxSize, with no appender/encoder coordination needed at all.
 * <p>
 * These tests assert actual capacity() values before/after clear(), not just that
 * isOversized() flips back to false, to prove the backing storage genuinely shrank rather
 * than just re-deriving the same predicate under test.
 */
class BufferSelfShrinkTest {

	private static final LogFormatter FORMATTER = LogFormatter.builder().message().build();

	private static LogEvent event(String message) {
		return LogEvent.of(System.Logger.Level.INFO, "test", message, KeyValues.of(), null);
	}

	@Test
	void stringBuilderBufferShrinksAfterOversizedClear() {
		LogEncoder encoder = LogEncoder.builder(FORMATTER).charset(StandardCharsets.UTF_8).maxSize(100).build();
		var buffer = (StringBuilderBuffer) encoder.buffer(WriteMethod.STRING);

		encoder.encode(event("x".repeat(2000)), buffer);
		int grownCapacity = buffer.stringBuilder.capacity();
		assertTrue(grownCapacity > 100, "sanity check: the big message must have actually grown the buffer");

		buffer.clear();

		assertTrue(buffer.stringBuilder.capacity() < grownCapacity,
				"clear() must shrink the backing StringBuilder back down once oversized");
	}

	@Test
	void stringBuilderBufferUnderThresholdIsLeftAlone() {
		LogEncoder encoder = LogEncoder.builder(FORMATTER).charset(StandardCharsets.UTF_8).maxSize(100_000).build();
		var buffer = (StringBuilderBuffer) encoder.buffer(WriteMethod.STRING);

		encoder.encode(event("small"), buffer);
		int capacityBeforeClear = buffer.stringBuilder.capacity();

		buffer.clear();

		assertEquals(capacityBeforeClear, buffer.stringBuilder.capacity(),
				"a buffer well under the threshold must not be reallocated on every clear()");
	}

	/*
	 * maxSize is deliberately set above
	 * DirectByteBufferBuffer.DEFAULT_INITIAL_BYTE_CAPACITY (8192) here - a maxSize at or
	 * below that makes the buffer unconditionally oversized from construction alone
	 * (documented on the buffer's own constructor), which would demonstrate that caveat
	 * instead of genuine event-driven growth.
	 */
	@Test
	void directByteBufferBufferShrinksBothStoresAfterOversizedClear() {
		LogEncoder encoder = LogEncoder.builder(FORMATTER).charset(StandardCharsets.UTF_8).maxSize(10_000).build();
		var buffer = (DirectByteBufferBuffer) encoder.buffer(WriteMethod.BYTE_BUFFER);
		var output = new CapturingOutput();

		encoder.encode(event("x".repeat(20_000)), buffer);
		int grownStringCapacity = buffer.stringBuilder.capacity();
		buffer.drain(output, event("unused"));
		int grownByteCapacity = output.lastCapacity;
		assertTrue(grownStringCapacity + grownByteCapacity > 10_000,
				"sanity check: the big message must have actually grown both stores past the threshold");

		buffer.clear();

		assertTrue(buffer.stringBuilder.capacity() < grownStringCapacity,
				"clear() must shrink the backing StringBuilder back down once oversized");

		// Encode a small event so encodeToByteBuffer() doesn't need to regrow the
		// now-shrunk ByteBuffer, then drain again to observe its new capacity.
		encoder.encode(event("small"), buffer);
		buffer.drain(output, event("unused"));
		assertTrue(output.lastCapacity < grownByteCapacity,
				"clear() must also reallocate the backing ByteBuffer back down once oversized");
	}

	@Test
	void directByteBufferBufferUnderThresholdIsLeftAlone() {
		LogEncoder encoder = LogEncoder.builder(FORMATTER).charset(StandardCharsets.UTF_8).maxSize(100_000).build();
		var buffer = (DirectByteBufferBuffer) encoder.buffer(WriteMethod.BYTE_BUFFER);
		var output = new CapturingOutput();

		encoder.encode(event("small"), buffer);
		int stringCapacityBeforeClear = buffer.stringBuilder.capacity();
		buffer.drain(output, event("unused"));
		int byteCapacityBeforeClear = output.lastCapacity;

		buffer.clear();
		encoder.encode(event("small"), buffer);
		buffer.drain(output, event("unused"));

		assertEquals(stringCapacityBeforeClear, buffer.stringBuilder.capacity(),
				"a buffer well under the threshold must not have its StringBuilder reallocated");
		assertEquals(byteCapacityBeforeClear, output.lastCapacity,
				"a buffer well under the threshold must not have its ByteBuffer reallocated");
	}

	/*
	 * Confirms gap #3 from the previous round of this exploration (batch
	 * append(LogEvent[], int) not covered by the appender-level replacement mechanism) is
	 * closed as a side effect of moving the shrink into clear() itself: the default
	 * LogOutput#write(LogEvent[], int, LogEncoder, Buffer) loop both real thread-local-
	 * buffer appenders use for their batch path already calls buffer.clear() once per
	 * event, so a big event early in one batch shrinks the shared buffer back down before
	 * the very next event in that same batch, with no separate wiring needed for the
	 * batch path at all - exercised here through a real
	 * LockThreadLocalBufferLogAppender.append(LogEvent[], int) call, not just the default
	 * method in isolation.
	 */
	@Test
	void batchAppendPathAlsoShrinksOversizedBufferBetweenEventsInTheSameBatch() {
		LogEncoder encoder = LogEncoder.builder(FORMATTER).charset(StandardCharsets.UTF_8).maxSize(10_000).build();
		var output = new CapturingOutput();
		var appender = new LockThreadLocalBufferLogAppender("test", output, encoder, EnumSet.noneOf(AppenderFlag.class),
				new ReentrantLock());

		appender.append(new LogEvent[] { event("x".repeat(20_000)), event("small") }, 2);

		assertTrue(output.capacities.get(0) > 10_000,
				"sanity check: the first (big) event in the batch must have grown the shared buffer");
		assertTrue(output.capacities.get(1) < output.capacities.get(0),
				"the second event in the same batch must already see the buffer shrunk back down, proving the "
						+ "batch path is covered without any appender-level wiring");
	}

	static class CapturingOutput implements LogOutput {

		int lastCapacity = -1;

		final List<Integer> capacities = new ArrayList<>();

		@Override
		public LogEncoder.BufferHints bufferHints() {
			return WriteMethod.BYTE_BUFFER;
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
		public void write(LogEvent event, ByteBuffer buf, ContentType contentType) {
			lastCapacity = buf.capacity();
			capacities.add(lastCapacity);
		}

		@Override
		public void write(LogEvent event, byte[] bytes, int off, int len, ContentType contentType) {
			throw new UnsupportedOperationException();
		}

		@Override
		public void flush() {
		}

		@Override
		public void close() {
		}

	}

}

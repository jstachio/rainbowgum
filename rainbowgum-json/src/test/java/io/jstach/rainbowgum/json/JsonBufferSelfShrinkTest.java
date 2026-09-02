package io.jstach.rainbowgum.json;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import io.jstach.rainbowgum.json.JsonBuffer.ExtendedFieldPrefix;

/*
 * Buffer#clear() self-shrinking for JsonBuffer, the JSON module's own Buffer
 * implementation - mirrors core's BufferSelfShrinkTest, but for JsonBuffer's two backing
 * stores (RawJsonWriter's byte array, and getFormattedMessageBuilder()'s StringBuilder)
 * which, per Adam, share ONE combined maxBufferSize threshold for simplicity rather than
 * being tracked separately.
 * <p>
 * JsonBuffer's raw JSON writer pre-allocates JsonBuffer.DEFAULT_INITIAL_JSON_CAPACITY
 * (8192) bytes unconditionally at construction, plus DEFAULT_INITIAL_MESSAGE_CAPACITY
 * (128) chars for the message builder - a combined baseline around 8320 present before a
 * single byte is ever written, the same caveat DirectByteBufferBuffer documents in core.
 * Thresholds here are picked comfortably above that baseline so growth is what tips a
 * buffer over, not the fixed initial allocation alone.
 */
class JsonBufferSelfShrinkTest {

	private static JsonBuffer buffer(int maxBufferSize) {
		return new JsonBuffer(false, ExtendedFieldPrefix.UNDERSCORE, maxBufferSize);
	}

	@Test
	void growingRawJsonWriterPastThresholdReportsOversized() {
		var buffer = buffer(20_000);
		buffer.write("key", "x".repeat(20_000), 0);
		assertTrue(buffer.isOversized());
	}

	@Test
	void growingFormattedMessageBuilderPastThresholdReportsOversized() {
		var buffer = buffer(20_000);
		buffer.getFormattedMessageBuilder().append("x".repeat(30_000));
		assertTrue(buffer.isOversized());
	}

	@Test
	void underThresholdIsNotOversized() {
		var buffer = buffer(100_000);
		buffer.write("key", "short", 0);
		assertFalse(buffer.isOversized());
	}

	@Test
	void unsetThresholdNeverReportsOversized() {
		var buffer = buffer(-1);
		buffer.write("key", "x".repeat(20_000), 0);
		buffer.getFormattedMessageBuilder().append("x".repeat(30_000));
		assertFalse(buffer.isOversized());
	}

	@Test
	void clearShrinksBothStoresOnceOversized() {
		var buffer = buffer(20_000);
		buffer.write("key", "x".repeat(20_000), 0);
		buffer.getFormattedMessageBuilder().append("x".repeat(30_000));
		assertTrue(buffer.isOversized(), "sanity check: must actually be oversized before clear()");

		buffer.clear();

		assertFalse(buffer.isOversized(), "clear() must shrink both backing stores back under threshold");
	}

	@Test
	void clearLeavesUnderThresholdBufferAlone() {
		var buffer = buffer(100_000);
		buffer.write("key", "short", 0);
		buffer.clear();
		assertFalse(buffer.isOversized());
	}

}

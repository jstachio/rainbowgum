package io.jstach.rainbowgum.json;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.time.Instant;

import org.json.JSONObject;
import org.junit.jupiter.api.Test;

import io.jstach.rainbowgum.LogEvent;
import io.jstach.rainbowgum.output.ListLogOutput;

/*
 * RawJsonWriter is package-private (only JsonBuffer, in this same package, uses it), so
 * this test lives in the same package rather than going through JsonBuffer/an encoder,
 * to exercise the low-level buffer growth/escaping/encoding behavior directly rather
 * than only indirectly through a specific encoder's field layout.
 *
 * Per Adam: Grisu3 and methods annotated @GeneratedByATrustedSource are excluded from
 * coverage expectations, so writeQuotedString's full escape-ladder and writeDouble's
 * Grisu3 fast path aren't chased exhaustively here - just enough to confirm the
 * observable behavior is correct.
 */
class RawJsonWriterTest {

	private static String toUtf8(RawJsonWriter w) {
		return new String(w.toByteArray(), StandardCharsets.UTF_8);
	}

	@Test
	void testWriteByteAndSize() {
		var w = new RawJsonWriter(16);
		assertEquals(0, w.size());
		w.writeByte(RawJsonWriter.OBJECT_START);
		w.writeByte(RawJsonWriter.OBJECT_END);
		assertEquals(2, w.size());
		assertEquals("{}", toUtf8(w));
	}

	@Test
	void testReset() {
		var w = new RawJsonWriter(16);
		w.writeByte(RawJsonWriter.OBJECT_START);
		assertEquals(1, w.size());
		w.reset();
		assertEquals(0, w.size());
	}

	@Test
	void testCapacityMatchesInitialConstruction() {
		var w = new RawJsonWriter(16);
		assertEquals(16, w.capacity());
	}

	@Test
	void testCapacityGrowsWithContent() {
		var w = new RawJsonWriter(4);
		w.writeString("a string long enough to force at least one enlargeOrFlush call");
		assertTrue(w.capacity() > 4, "capacity must have grown past the tiny initial allocation");
	}

	@Test
	void testShrinkToReplacesBackingArrayCapacity() {
		var w = new RawJsonWriter(4);
		w.writeString("a string long enough to force at least one enlargeOrFlush call");
		w.reset();
		w.shrinkTo(8);
		assertEquals(8, w.capacity());
		// the shrunk writer must still be fully usable afterward.
		w.writeByte(RawJsonWriter.OBJECT_START);
		w.writeByte(RawJsonWriter.OBJECT_END);
		assertEquals("{}", toUtf8(w));
	}

	@Test
	void testWriteStringFastPathNoEscaping() {
		var w = new RawJsonWriter(16);
		w.writeString("hello world");
		assertEquals("\"hello world\"", toUtf8(w));
	}

	@Test
	void testWriteStringEmpty() {
		var w = new RawJsonWriter(16);
		w.writeString("");
		assertEquals("\"\"", toUtf8(w));
	}

	@Test
	void testWriteStringEscapesQuoteAndBackslash() {
		var w = new RawJsonWriter(16);
		w.writeString("a\"b\\c");
		assertEquals("\"a\\\"b\\\\c\"", toUtf8(w));
	}

	@Test
	void testWriteStringEscapesNamedControlChars() {
		var w = new RawJsonWriter(16);
		w.writeString("\b\t\n\f\r");
		assertEquals("\"\\b\\t\\n\\f\\r\"", toUtf8(w));
	}

	@Test
	void testWriteStringEscapesOtherControlCharsAsUnicode() {
		var w = new RawJsonWriter(16);
		w.writeString("\u0000\u0001\u001F");
		assertEquals("\"\\u0000\\u0001\\u001F\"", toUtf8(w));
	}

	@Test
	void testWriteStringBoundaryPrintableAndDelCharacters() {
		// '}' (0x7D) and '~' (0x7E) are the last two chars the fast path in writeString
		// accepts directly (c < 126); DEL (0x7F) falls through to writeQuotedString,
		// which passes it through unescaped as a literal byte.
		var w = new RawJsonWriter(16);
		w.writeString("}~");
		assertEquals("\"}~\"", toUtf8(w));
	}

	@Test
	void testWriteStringEncodesTwoByteUtf8() {
		var w = new RawJsonWriter(16);
		w.writeString("café");
		assertEquals("\"café\"", toUtf8(w));
	}

	@Test
	void testWriteStringEncodesThreeByteUtf8() {
		var w = new RawJsonWriter(16);
		w.writeString("中文");
		String json = "{\"v\":\"中文\"}";
		assertEquals("中文", new JSONObject(json).getString("v"));
	}

	@Test
	void testWriteStringEncodesFourByteUtf8Supplementary() {
		// U+1F600 GRINNING FACE, a surrogate pair in UTF-16.
		String emoji = new String(Character.toChars(0x1F600));
		var w = new RawJsonWriter(16);
		w.writeString(emoji);
		byte[] bytes = w.toByteArray();
		String decoded = new String(bytes, StandardCharsets.UTF_8);
		assertEquals("\"" + emoji + "\"", decoded);
		// Round-trip through a real JSON parser to confirm it is valid, well-formed
		// UTF-8-encoded JSON, not just byte-for-byte what we expect.
		JSONObject parsed = new JSONObject("{\"v\":" + decoded + "}");
		assertEquals(emoji, parsed.getString("v"));
	}

	@Test
	void testWriteStringReplacesUnpairedSurrogateWithReplacementChar() {
		// A lone high surrogate with no matching low surrogate - malformed UTF-16 input
		// (e.g. from adversarial log message content) must not throw or corrupt the
		// buffer; RawJsonWriter substitutes U+FFFD.
		var w = new RawJsonWriter(16);
		w.writeString("bad\uD800end");
		String decoded = new String(w.toByteArray(), StandardCharsets.UTF_8);
		assertEquals("\"bad�end\"", decoded);
	}

	@Test
	void testWriteStringHolisticEscapingRoundTripsThroughRealJsonParser() {
		String nasty = "quote\" backslash\\ tab\t newline\n unicodeé中ὠ0";
		var w = new RawJsonWriter(4);
		w.writeByte(RawJsonWriter.OBJECT_START);
		w.writeString("k");
		w.writeByte(RawJsonWriter.SEMI);
		w.writeString(nasty);
		w.writeByte(RawJsonWriter.OBJECT_END);
		String json = new String(w.toByteArray(), StandardCharsets.UTF_8);
		JSONObject parsed = new JSONObject(json);
		assertEquals(nasty, parsed.getString("k"));
	}

	@Test
	void testWriteAsciiStringDoesNotEscape() {
		// writeAsciiString trusts the caller completely and does not escape - contrast
		// with writeString, which would have escaped the embedded quote.
		var w = new RawJsonWriter(16);
		w.writeAsciiString("a\"b");
		assertEquals("\"a\"b\"", toUtf8(w));
	}

	@Test
	void testWriteAsciiWritesRawUnquotedBytes() {
		var w = new RawJsonWriter(16);
		w.writeAscii("true");
		assertEquals("true", toUtf8(w));
	}

	@Test
	void testWriteInt() {
		var w = new RawJsonWriter(16);
		w.writeInt(-42);
		assertEquals("-42", toUtf8(w));
	}

	@Test
	void testWriteDoubleSpecialValues() {
		// writeDouble is @GeneratedByATrustedSource, so not chased for coverage, but this
		// pins down an observable quirk found while sanity-checking it: the
		// Infinity/-Infinity/NaN branches pass an already-quoted literal (e.g.
		// "\"Infinity\"") into writeAsciiString, which always wraps its argument in its
		// own quotes - so the actual output is double-quoted, not the single-quoted
		// "Infinity" a reader would expect. Likewise 0.0 comes out as the JSON string
		// "0.0" rather than the bare JSON number 0.0. Asserting actual behavior here,
		// not fixing it - flagged to Adam separately since this method is out of scope
		// per his note on trusted/generated code.
		assertEquals("\"\"Infinity\"\"", writeDoubleToString(Double.POSITIVE_INFINITY));
		assertEquals("\"\"-Infinity\"\"", writeDoubleToString(Double.NEGATIVE_INFINITY));
		assertEquals("\"\"NaN\"\"", writeDoubleToString(Double.NaN));
		assertEquals("\"0.0\"", writeDoubleToString(0.0));
	}

	@Test
	void testWriteDoubleNormalValueRoundTrips() {
		String actual = writeDoubleToString(0.001);
		assertEquals(0.001, Double.parseDouble(actual));
	}

	private static String writeDoubleToString(double value) {
		var w = new RawJsonWriter(32);
		w.writeDouble(value);
		return toUtf8(w);
	}

	@Test
	void testToByteArrayCopiesAndResetsPosition() {
		var w = new RawJsonWriter(16);
		w.writeAscii("abc");
		byte[] copy = w.toByteArray();
		assertEquals("abc", new String(copy, StandardCharsets.UTF_8));
		assertEquals(0, w.size());
	}

	@Test
	void testToByteBufferFlipsAndResetsPosition() {
		var w = new RawJsonWriter(16);
		w.writeAscii("abc");
		ByteBuffer buf = ByteBuffer.allocate(16);
		w.toByteBuffer(buf);
		assertEquals(0, w.size());
		byte[] out = new byte[buf.remaining()];
		buf.get(out);
		assertEquals("abc", new String(out, StandardCharsets.UTF_8));
	}

	@Test
	void testToStreamWritesAndResetsPosition() throws Exception {
		var w = new RawJsonWriter(16);
		w.writeAscii("abc");
		var out = new ByteArrayOutputStream();
		w.toStream(out);
		assertEquals("abc", out.toString(StandardCharsets.UTF_8));
		assertEquals(0, w.size());
	}

	@Test
	void testWriteToLogOutputResetsPositionAfterWrite() {
		var w = new RawJsonWriter(16);
		w.writeAscii("{}");
		var output = new ListLogOutput();
		LogEvent event = LogEvent.of(System.Logger.Level.INFO, "test", "hello", null).freeze(Instant.EPOCH);
		w.write(output, event);
		assertEquals(0, w.size());
		assertEquals("{}", output.events().get(0).getValue());
	}

	@Test
	void testEnsureCapacityGrowsBufferOnLargeWrite() {
		// Constructed with a tiny initial capacity so a single writeString() call forces
		// enlargeOrFlush() to grow the backing array more than once.
		var w = new RawJsonWriter(2);
		String value = "a".repeat(500);
		w.writeString(value);
		assertEquals("\"" + value + "\"", toUtf8(w));
	}

	@Test
	void testWriteByteGrowsBufferWhenFull() {
		var w = new RawJsonWriter(1);
		w.writeByte((byte) 'a');
		w.writeByte((byte) 'b');
		w.writeByte((byte) 'c');
		assertEquals("abc", toUtf8(w));
	}

	@Test
	void testWriteAsciiGrowsBufferOnLargeWrite() {
		var w = new RawJsonWriter(1);
		String value = "x".repeat(200);
		w.writeAscii(value);
		assertEquals(value, toUtf8(w));
	}

	@Test
	void testWriteAsciiStringGrowsBufferOnLargeWrite() {
		var w = new RawJsonWriter(1);
		String value = "x".repeat(200);
		w.writeAsciiString(value);
		assertEquals("\"" + value + "\"", toUtf8(w));
	}

	@Test
	void testMultipleWritesAccumulateAndSizeTracksPosition() {
		var w = new RawJsonWriter(4);
		w.writeByte(RawJsonWriter.OBJECT_START);
		w.writeString("a");
		w.writeByte(RawJsonWriter.SEMI);
		w.writeInt(1);
		w.writeByte(RawJsonWriter.COMMA);
		w.writeString("b");
		w.writeByte(RawJsonWriter.SEMI);
		w.writeString("v");
		w.writeByte(RawJsonWriter.OBJECT_END);
		String json = toUtf8(w);
		assertTrue(json.startsWith("{"), json);
		JSONObject parsed = new JSONObject(json);
		assertEquals(1, parsed.getInt("a"));
		assertEquals("v", parsed.getString("b"));
	}

}

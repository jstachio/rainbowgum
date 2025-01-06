package io.jstach.rainbowgum.json;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.util.Arrays;

import io.jstach.rainbowgum.LogEvent;
import io.jstach.rainbowgum.LogOutput;
import io.jstach.rainbowgum.LogOutput.ContentType.StandardContentType;
import io.jstach.rainbowgum.annotation.GeneratedByATrustedSource;

/*
 * This code was largely inspired a long time ago
 * by DSL platform JSON writer.
 * https://github.com/ngs-doo/dsl-json
 */
class RawJsonWriter {

	private int position;

	private byte[] buffer;

	/**
	 * Helper for writing JSON object start: {
	 */
	public static final byte OBJECT_START = '{';

	/**
	 * Helper for writing JSON object end: }
	 */
	public static final byte OBJECT_END = '}';

	/**
	 * Helper for writing JSON array start: [
	 */
	public static final byte ARRAY_START = '[';

	/**
	 * Helper for writing JSON array end: ]
	 */
	public static final byte ARRAY_END = ']';

	/**
	 * Helper for writing comma separator: ,
	 */
	public static final byte COMMA = ',';

	/**
	 * Helper for writing semicolon: :
	 */
	public static final byte SEMI = ':';

	/**
	 * Helper for writing JSON quote: "
	 */
	public static final byte QUOTE = '"';

	/**
	 * Helper for writing JSON escape: \\
	 */
	public static final byte ESCAPE = '\\';

	private final Grisu3.FastDtoaBuilder doubleBuilder = new Grisu3.FastDtoaBuilder();

	public RawJsonWriter(int capacity) {
		this.buffer = new byte[capacity];
	}

	final byte[] ensureCapacity(final int free) {
		if (position + free >= buffer.length) {
			enlargeOrFlush(position, free);
		}
		return buffer;
	}

	void advance(int size) {
		position += size;
	}

	private void enlargeOrFlush(final int size, final int padding) {
		buffer = Arrays.copyOf(buffer, buffer.length + buffer.length / 2 + padding);
	}

	/**
	 * Write a single byte into the JSON.
	 * @param value byte to write into the JSON
	 */
	public final void writeByte(final byte value) {
		if (position == buffer.length) {
			enlargeOrFlush(position, 0);
		}
		buffer[position++] = value;
	}

	/**
	 * Write a quoted string into the JSON. String will be appropriately escaped according
	 * to JSON escaping rules.
	 * @param value string to write
	 */
	public final void writeString(final String value) {
		final int len = value.length();
		if (position + (len << 2) + (len << 1) + 2 >= buffer.length) {
			enlargeOrFlush(position, (len << 2) + (len << 1) + 2);
		}
		final byte[] _result = buffer;
		_result[position] = QUOTE;
		int cur = position + 1;
		for (int i = 0; i < len; i++) {
			final char c = value.charAt(i);
			if (c > 31 && c != '"' && c != '\\' && c < 126) {
				_result[cur++] = (byte) c;
			}
			else {
				writeQuotedString(value, i, cur, len);
				return;
			}
		}
		_result[cur] = QUOTE;
		position = cur + 1;
	}

	@GeneratedByATrustedSource
	private void writeQuotedString(final CharSequence str, int i, int cur, final int len) {
		final byte[] _result = this.buffer;
		for (; i < len; i++) {
			final char c = str.charAt(i);
			if (c == '"') {
				_result[cur++] = ESCAPE;
				_result[cur++] = QUOTE;
			}
			else if (c == '\\') {
				_result[cur++] = ESCAPE;
				_result[cur++] = ESCAPE;
			}
			else if (c < 32) {
				if (c == 8) {
					_result[cur++] = ESCAPE;
					_result[cur++] = 'b';
				}
				else if (c == 9) {
					_result[cur++] = ESCAPE;
					_result[cur++] = 't';
				}
				else if (c == 10) {
					_result[cur++] = ESCAPE;
					_result[cur++] = 'n';
				}
				else if (c == 12) {
					_result[cur++] = ESCAPE;
					_result[cur++] = 'f';
				}
				else if (c == 13) {
					_result[cur++] = ESCAPE;
					_result[cur++] = 'r';
				}
				else {
					_result[cur] = ESCAPE;
					_result[cur + 1] = 'u';
					_result[cur + 2] = '0';
					_result[cur + 3] = '0';
					switch (c) {
						case 0:
							_result[cur + 4] = '0';
							_result[cur + 5] = '0';
							break;
						case 1:
							_result[cur + 4] = '0';
							_result[cur + 5] = '1';
							break;
						case 2:
							_result[cur + 4] = '0';
							_result[cur + 5] = '2';
							break;
						case 3:
							_result[cur + 4] = '0';
							_result[cur + 5] = '3';
							break;
						case 4:
							_result[cur + 4] = '0';
							_result[cur + 5] = '4';
							break;
						case 5:
							_result[cur + 4] = '0';
							_result[cur + 5] = '5';
							break;
						case 6:
							_result[cur + 4] = '0';
							_result[cur + 5] = '6';
							break;
						case 7:
							_result[cur + 4] = '0';
							_result[cur + 5] = '7';
							break;
						case 11:
							_result[cur + 4] = '0';
							_result[cur + 5] = 'B';
							break;
						case 14:
							_result[cur + 4] = '0';
							_result[cur + 5] = 'E';
							break;
						case 15:
							_result[cur + 4] = '0';
							_result[cur + 5] = 'F';
							break;
						case 16:
							_result[cur + 4] = '1';
							_result[cur + 5] = '0';
							break;
						case 17:
							_result[cur + 4] = '1';
							_result[cur + 5] = '1';
							break;
						case 18:
							_result[cur + 4] = '1';
							_result[cur + 5] = '2';
							break;
						case 19:
							_result[cur + 4] = '1';
							_result[cur + 5] = '3';
							break;
						case 20:
							_result[cur + 4] = '1';
							_result[cur + 5] = '4';
							break;
						case 21:
							_result[cur + 4] = '1';
							_result[cur + 5] = '5';
							break;
						case 22:
							_result[cur + 4] = '1';
							_result[cur + 5] = '6';
							break;
						case 23:
							_result[cur + 4] = '1';
							_result[cur + 5] = '7';
							break;
						case 24:
							_result[cur + 4] = '1';
							_result[cur + 5] = '8';
							break;
						case 25:
							_result[cur + 4] = '1';
							_result[cur + 5] = '9';
							break;
						case 26:
							_result[cur + 4] = '1';
							_result[cur + 5] = 'A';
							break;
						case 27:
							_result[cur + 4] = '1';
							_result[cur + 5] = 'B';
							break;
						case 28:
							_result[cur + 4] = '1';
							_result[cur + 5] = 'C';
							break;
						case 29:
							_result[cur + 4] = '1';
							_result[cur + 5] = 'D';
							break;
						case 30:
							_result[cur + 4] = '1';
							_result[cur + 5] = 'E';
							break;
						default:
							_result[cur + 4] = '1';
							_result[cur + 5] = 'F';
							break;
					}
					cur += 6;
				}
			}
			else if (c < 0x007F) {
				_result[cur++] = (byte) c;
			}
			else {
				final int cp = Character.codePointAt(str, i);
				if (Character.isSupplementaryCodePoint(cp)) {
					i++;
				}
				if (cp == 0x007F) {
					_result[cur++] = (byte) cp;
				}
				else if (cp <= 0x7FF) {
					_result[cur++] = (byte) (0xC0 | ((cp >> 6) & 0x1F));
					_result[cur++] = (byte) (0x80 | (cp & 0x3F));
				}
				else if ((cp < 0xD800) || (cp > 0xDFFF && cp <= 0xFFFF)) {
					_result[cur++] = (byte) (0xE0 | ((cp >> 12) & 0x0F));
					_result[cur++] = (byte) (0x80 | ((cp >> 6) & 0x3F));
					_result[cur++] = (byte) (0x80 | (cp & 0x3F));
				}
				else if (cp >= 0x10000 && cp <= 0x10FFFF) {
					_result[cur++] = (byte) (0xF0 | ((cp >> 18) & 0x07));
					_result[cur++] = (byte) (0x80 | ((cp >> 12) & 0x3F));
					_result[cur++] = (byte) (0x80 | ((cp >> 6) & 0x3F));
					_result[cur++] = (byte) (0x80 | (cp & 0x3F));
				}
				else {
					throw new IllegalArgumentException(
							"Unknown unicode codepoint in string! " + Integer.toHexString(cp));
				}
			}
		}
		_result[cur] = QUOTE;
		position = cur + 1;
	}

	/**
	 * Write a quoted string consisting of only ascii characters. String will not be
	 * escaped according to JSON escaping rules.
	 * @param value ascii string
	 */
	@SuppressWarnings("deprecation")
	public final void writeAsciiString(final String value) {
		final int len = value.length() + 2;
		if (position + len >= buffer.length) {
			enlargeOrFlush(position, len);
		}
		final byte[] _result = buffer;
		_result[position] = QUOTE;
		value.getBytes(0, len - 2, _result, position + 1);
		_result[position + len - 1] = QUOTE;
		position += len;
	}

	@GeneratedByATrustedSource
	public final void writeDouble(final double value) {
		if (value == Double.POSITIVE_INFINITY) {
			writeAsciiString("\"Infinity\"");
		}
		else if (value == Double.NEGATIVE_INFINITY) {
			writeAsciiString("\"-Infinity\"");
		}
		else if (value != value) {
			writeAsciiString("\"NaN\"");
		}
		else if (value == 0.0) {
			writeAsciiString("0.0");
		}
		else {
			if (Grisu3.tryConvert(value, doubleBuilder)) {
				if (position + 24 >= buffer.length) {
					enlargeOrFlush(position, 24);
				}
				final int len = doubleBuilder.copyTo(buffer, position);
				position += len;
			}
			else {
				writeAsciiString(Double.toString(value));
			}
		}
	}

	/**
	 * Write string consisting of only ascii characters. String will not be escaped
	 * according to JSON escaping rules.
	 * @param value ascii string
	 */
	@SuppressWarnings("deprecation")
	public final void writeAscii(final String value) {
		final int len = value.length();
		if (position + len >= buffer.length) {
			enlargeOrFlush(position, len);
		}
		value.getBytes(0, len, buffer, position);
		position += len;
	}

	public final void writeInt(final int value) {
		writeAscii(Integer.toString(value));
	}

	/**
	 * Content of buffer can be copied to another array of appropriate size. This method
	 * can't be used when targeting output stream. Ideally it should be avoided if
	 * possible, since it will create an array copy. It's better to use getByteBuffer and
	 * size instead.
	 * @return copy of the buffer up to the current position
	 */
	@GeneratedByATrustedSource
	final byte[] toByteArray() {
		var r = Arrays.copyOf(buffer, position);
		reset();
		return r;
	}

	@GeneratedByATrustedSource
	void toByteBuffer(ByteBuffer b) {
		b.put(buffer, 0, position);
		b.flip();
		reset();
	}

	/**
	 * When JsonWriter does not target stream, this method should be used to copy content
	 * of the buffer into target stream. It will also reset the buffer position to 0 so
	 * writer can be continued to be used even without a call to reset().
	 * @param stream target stream
	 * @throws IOException propagates from stream.write
	 */
	@GeneratedByATrustedSource
	final void toStream(final OutputStream stream) throws IOException {
		stream.write(buffer, 0, position);
		position = 0;
	}

	public final void write(final LogOutput output, LogEvent event) {
		output.write(event, buffer, 0, position, StandardContentType.APPLICATION_JSON);
		position = 0;
	}

	/**
	 * Current position in the buffer. When stream is not used, this is also equivalent to
	 * the size of the resulting JSON in bytes
	 * @return position in the populated buffer
	 */
	public final int size() {
		return position;
	}

	/**
	 * Resets the writer
	 */
	public final void reset() {
		position = 0;
	}

}

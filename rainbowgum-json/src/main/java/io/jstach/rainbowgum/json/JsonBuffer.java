package io.jstach.rainbowgum.json;

import io.jstach.rainbowgum.LogEncoder.Buffer;

import static io.jstach.rainbowgum.json.RawJsonWriter.COMMA;
import static io.jstach.rainbowgum.json.RawJsonWriter.SEMI;

import org.eclipse.jdt.annotation.Nullable;

import io.jstach.rainbowgum.LogEvent;
import io.jstach.rainbowgum.LogOutput;

/**
 * A buffer designed for encoding JSON efficiently.
 */
public final class JsonBuffer implements Buffer {

	private final RawJsonWriter jsonWriter = new RawJsonWriter(1024 * 8);

	private final StringBuilder formattedMessageBuilder = new StringBuilder();

	private final boolean prettyprint;

	private final ExtendedFieldPrefix extendedFieldPrefix;

	/**
	 * A flag to indicate this field is extended which means it will be prefixed with
	 * {@link ExtendedFieldPrefix}.
	 */
	public static final int EXTENDED_F = 0x00000002;

	protected static final byte DEFAULT_EXTENDED_FIELD_PREFIX = '_';

	/**
	 * Create a json buffer.
	 * @param prettyprint whether or not to pretty print the JSON.
	 * @param extendedFieldPrefix prefix for extended fields.
	 */
	public JsonBuffer(boolean prettyprint, ExtendedFieldPrefix extendedFieldPrefix) {
		super();
		this.prettyprint = prettyprint;
		this.extendedFieldPrefix = extendedFieldPrefix;
	}

	@Override
	public void drain(LogOutput output, LogEvent event) {
		jsonWriter.write(output, event);
		clear();
	}

	@Override
	public void clear() {
		jsonWriter.reset();
		formattedMessageBuilder.setLength(0);
	}

	/**
	 * Reusable String buffer for formatted messages.
	 * @return buffer.
	 */
	public StringBuilder getFormattedMessageBuilder() {
		return formattedMessageBuilder;
	}

	/**
	 * JSON tokens.
	 */
	public enum JSONToken {

		/**
		 * Helper for writing JSON object start: {
		 */
		OBJECT_START(RawJsonWriter.OBJECT_START),

		/**
		 * Helper for writing JSON object end: }
		 */
		OBJECT_END(RawJsonWriter.OBJECT_END),

		/**
		 * Helper for writing JSON array start: [
		 */
		ARRAY_START(RawJsonWriter.ARRAY_START),

		/**
		 * Helper for writing JSON array end: ]
		 */
		ARRAY_END(RawJsonWriter.ARRAY_END),

		/**
		 * Helper for writing comma separator: ,
		 */
		COMMA(RawJsonWriter.COMMA),

		/**
		 * Helper for writing semicolon: :
		 */
		SEMI(RawJsonWriter.SEMI),

		/**
		 * Helper for writing JSON quote: "
		 */
		QUOTE(RawJsonWriter.QUOTE),

		/**
		 * Helper for writing JSON escape: \\
		 */
		ESCAPE(RawJsonWriter.ESCAPE);

		final byte raw;

		private JSONToken(byte raw) {
			this.raw = (byte) raw;
		}

	}

	private static final byte UNDERSCORE_PREFIX = '_';

	private static final byte AT_PREFIX = '@';

	private static byte LF = '\n';

	private static byte SPACE = ' ';

	/**
	 * Extended fields are just fields that have some special prefix for things like GELF
	 * and ECS.
	 */
	public enum ExtendedFieldPrefix {

		/**
		 * "_" Underscore prefix
		 */
		UNDERSCORE(UNDERSCORE_PREFIX),
		/**
		 * "@" At symbol prefix
		 */
		AT(AT_PREFIX);

		private final byte raw;

		private ExtendedFieldPrefix(byte raw) {
			this.raw = raw;
		}

	}

	/**
	 * Writes a JSON token.
	 * @param token token not null.
	 */
	public final void write(JSONToken token) {
		jsonWriter.writeByte(token.raw);
	}

	/**
	 * Efficiently writes a line feed.
	 */
	public final void writeLineFeed() {
		jsonWriter.writeByte(LF);
	}

	/**
	 * Writes a string field.
	 * @param k field name
	 * @param v value
	 * @param index the current index for comma determination
	 * @return index + 1
	 */
	public final int write(String k, @Nullable String v, int index) {
		return write(k, v, index, 0);
	}

	/**
	 * Writes a string field.
	 * @param k field name
	 * @param v value
	 * @param index the current index for comma determination
	 * @param flag see {@link #EXTENDED_F}
	 * @return index + 1
	 */
	public final int write(String k, @Nullable String v, int index, int flag) {
		if (v == null)
			return index;
		_writeStartField(k, index, flag);
		jsonWriter.writeString(v);
		_writeEndField(flag);
		return index + 1;

	}

	/**
	 * Writes a double field.
	 * @param k field name
	 * @param v value
	 * @param index the current index for comma determination
	 * @param flag see {@link #EXTENDED_F}
	 * @return index + 1
	 */
	public final int writeDouble(String k, double v, int index, int flag) {
		_writeStartField(k, index, flag);
		jsonWriter.writeDouble(v);
		_writeEndField(flag);
		return index + 1;
	}

	/**
	 * Writes a string field.
	 * @param k field name
	 * @param v value
	 * @param index the current index for comma determination
	 * @param flag see {@link #EXTENDED_F}
	 * @return index + 1
	 */
	public final int writeInt(String k, int v, int index, int flag) {
		_writeStartField(k, index, flag);
		jsonWriter.writeInt(v);
		_writeEndField(flag);
		return index + 1;
	}

	private final void _writeStartField(String k, int index, int flag) {
		if (index > 0) {
			jsonWriter.writeByte(COMMA);
		}
		if (prettyprint) {
			jsonWriter.writeByte(LF);
		}
		if (prettyprint) {
			jsonWriter.writeByte(SPACE);
		}
		if ((flag & EXTENDED_F) == EXTENDED_F) {
			jsonWriter.writeByte(extendedFieldPrefix.raw);
		}
		jsonWriter.writeAsciiString(k);
		jsonWriter.writeByte(SEMI);
	}

	private static final void _writeEndField(int flag) {
		// ignore for now.
	}

}

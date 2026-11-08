package io.jstach.rainbowgum.format;

import java.util.List;

import org.jspecify.annotations.Nullable;

import io.jstach.rainbowgum.KeyValues;
import io.jstach.rainbowgum.KeyValues.KeyValuesConsumer;
import io.jstach.rainbowgum.LogEvent;
import io.jstach.rainbowgum.LogFormatter;
import io.jstach.rainbowgum.LogFormatter.KeyValueNullStrategy;

/*
 * logfmt key values for KeyValuesFormatterBuilder, and logfmt's quoting and escaping,
 * which LogfmtFormatter, the logfmt encoder, also uses for its own fields.
 */
record LogfmtKeyValuesFormatter(@Nullable List<String> keys,
		KeyValueNullStrategy nullStrategy) implements LogFormatter.EventFormatter, KeyValuesConsumer<StringBuilder> {

	/*
	 * All key values, a null value written as key=.
	 */
	static final LogfmtKeyValuesFormatter ALL = new LogfmtKeyValuesFormatter(null, KeyValueNullStrategy.KEEP);

	@Override
	public void format(StringBuilder output, LogEvent event) {
		var keyValues = event.keyValues();
		var selected = keys;
		if (selected == null) {
			keyValues.forEach(this, 0, output);
		}
		else {
			int count = 0;
			for (String key : selected) {
				count = accept(keyValues, key, keyValues.getValueOrNull(key), count, output);
			}
		}
	}

	@Override
	public int accept(KeyValues values, String key, @Nullable String value, int count, StringBuilder output) {
		if (value == null) {
			switch (nullStrategy) {
				case SKIP -> {
					return count;
				}
				case EMPTY -> value = "";
				case KEEP -> {
					/* written as key= */
				}
			}
		}
		if (count > 0) {
			output.append(' ');
		}
		appendKey(output, key);
		output.append('=');
		appendValue(output, value);
		return count + 1;
	}

	static void appendValue(StringBuilder output, @Nullable String value) {
		if (value == null) {
			return;
		}
		int start = output.length();
		output.append(value);
		quoteInPlace(output, start);
	}

	/*
	 * Percent-encodes the characters logfmt keys do not allow, and % itself, so two
	 * different keys never write the same name. The empty key is a lone %, which no
	 * encoded non-empty key can be.
	 */
	static void appendKey(StringBuilder output, String key) {
		if (key.isEmpty()) {
			output.append('%');
			return;
		}
		for (int i = 0; i < key.length(); i++) {
			char c = key.charAt(i);
			if (isSpecial(c) || c == '\\' || c == '%') {
				output.append('%').append(HEX[c >> 4]).append(HEX[c & 0xF]);
			}
			else if (isEscapedControl(c)) {
				appendPercentUtf8(output, c);
			}
			else {
				output.append(c);
			}
		}
	}

	// Uppercase for percent-encoding keys, as RFC 3986 recommends.
	private static final char[] HEX = "0123456789ABCDEF".toCharArray();

	// Lowercase for the backslash-u value escapes, matching the output before this table
	// existed.
	private static final String ESCAPE_HEX = "0123456789abcdef";

	/*
	 * The non-ASCII escaped controls are all in the Basic Multilingual Plane and none is
	 * a surrogate, so each is two or three UTF-8 bytes.
	 */
	private static void appendPercentUtf8(StringBuilder output, char c) {
		if (c < 0x800) {
			appendPercentByte(output, 0xC0 | (c >> 6));
		}
		else {
			appendPercentByte(output, 0xE0 | (c >> 12));
			appendPercentByte(output, 0x80 | ((c >> 6) & 0x3F));
		}
		appendPercentByte(output, 0x80 | (c & 0x3F));
	}

	private static void appendPercentByte(StringBuilder output, int b) {
		output.append('%').append(HEX[(b >> 4) & 0xF]).append(HEX[b & 0xF]);
	}

	/*
	 * Quotes and escapes the text from start to the end of the output if logfmt needs it,
	 * in place so the common unquoted case copies nothing. Replacing each escaped
	 * character shifts the rest of the text, which is quadratic for a message of many
	 * lines, so the quoted copy is appended after the text in one pass and the original
	 * is then deleted.
	 */
	static void quoteInPlace(StringBuilder output, int start) {
		int end = output.length();
		if (end > start && !needsQuotes(output, start, end)) {
			return;
		}
		output.append('"');
		for (int i = start; i < end; i++) {
			char c = output.charAt(i);
			switch (c) {
				case '"' -> output.append("\\\"");
				case '\\' -> output.append("\\\\");
				case '\n' -> output.append("\\n");
				case '\r' -> output.append("\\r");
				case '\t' -> output.append("\\t");
				default -> {
					if (isEscapedControl(c)) {
						output.append("\\u")
							.append(ESCAPE_HEX.charAt((c >>> 12) & 0xf))
							.append(ESCAPE_HEX.charAt((c >>> 8) & 0xf))
							.append(ESCAPE_HEX.charAt((c >>> 4) & 0xf))
							.append(ESCAPE_HEX.charAt(c & 0xf));
					}
					else {
						output.append(c);
					}
				}
			}
		}
		output.append('"');
		output.delete(start, end);
	}

	private static boolean needsQuotes(StringBuilder output, int start, int end) {
		for (int i = start; i < end; i++) {
			char c = output.charAt(i);
			if (isSpecial(c) || c == '\\' || isEscapedControl(c)) {
				return true;
			}
		}
		return false;
	}

	private static boolean isSpecial(char c) {
		return c <= ' ' || c == '=' || c == '"' || c == 0x7f;
	}

	/*
	 * Written as backslash-u escapes in values and percent-encoded in keys: C0 controls,
	 * DEL, C1 controls, the Unicode line and paragraph separators, and the bidirectional
	 * embedding, override and isolate characters, so a field can neither break the line,
	 * control a terminal nor visually reorder the line. The same set LTSV escapes.
	 */
	private static boolean isEscapedControl(char c) {
		return c < ' ' || (c >= '\u007f' && c <= '\u009f') || c == '\u2028' || c == '\u2029'
				|| (c >= '\u202a' && c <= '\u202e') || (c >= '\u2066' && c <= '\u2069');
	}

}

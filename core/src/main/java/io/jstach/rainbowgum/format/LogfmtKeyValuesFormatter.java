package io.jstach.rainbowgum.format;

import java.util.List;
import java.util.Locale;

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
			else {
				output.append(c);
			}
		}
	}

	private static final char[] HEX = "0123456789ABCDEF".toCharArray();

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
			String escape = escape(c);
			if (escape != null) {
				output.append(escape);
			}
			else {
				output.append(c);
			}
		}
		output.append('"');
		output.delete(start, end);
	}

	private static boolean needsQuotes(StringBuilder output, int start, int end) {
		for (int i = start; i < end; i++) {
			char c = output.charAt(i);
			if (isSpecial(c) || c == '\\') {
				return true;
			}
		}
		return false;
	}

	private static boolean isSpecial(char c) {
		return c <= ' ' || c == '=' || c == '"' || c == 0x7f;
	}

	private static @Nullable String escape(char c) {
		return switch (c) {
			case '"' -> "\\\"";
			case '\\' -> "\\\\";
			case '\n' -> "\\n";
			case '\r' -> "\\r";
			case '\t' -> "\\t";
			default -> c < ' ' || c == 0x7f ? String.format(Locale.ROOT, "\\u%04x", (int) c) : null;
		};
	}

}

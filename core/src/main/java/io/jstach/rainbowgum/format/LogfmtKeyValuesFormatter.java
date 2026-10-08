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

	static void appendKey(StringBuilder output, String key) {
		if (key.isEmpty()) {
			output.append('_');
			return;
		}
		for (int i = 0; i < key.length(); i++) {
			char c = key.charAt(i);
			output.append(isSpecial(c) || c == '\\' ? '_' : c);
		}
	}

	/*
	 * Quotes and escapes the text from start to the end of the output if logfmt needs it,
	 * in place so the common unquoted case copies nothing.
	 */
	static void quoteInPlace(StringBuilder output, int start) {
		int end = output.length();
		if (end > start && !needsQuotes(output, start, end)) {
			return;
		}
		output.insert(start, '"');
		for (int i = start + 1; i <= end; i++) {
			String escape = escape(output.charAt(i));
			if (escape != null) {
				output.replace(i, i + 1, escape);
				i += escape.length() - 1;
				end += escape.length() - 1;
			}
		}
		output.append('"');
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

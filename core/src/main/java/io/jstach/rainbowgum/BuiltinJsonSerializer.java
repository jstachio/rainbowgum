package io.jstach.rainbowgum;

import java.lang.reflect.Array;
import java.util.Map;

import org.jspecify.annotations.Nullable;

import io.jstach.rainbowgum.KeyValues.JsonSerializer;

/*
 * No reflection beyond java.lang.reflect.Array, so it works the same in a native image.
 * Nesting deeper than MAX_DEPTH (usually a cycle) is written as a string.
 */
enum BuiltinJsonSerializer implements JsonSerializer {

	INSTANCE;

	private static final int MAX_DEPTH = 32;

	@Override
	public @Nullable String toJson(Object value) {
		/*
		 * A top level value that would be a JSON string anyway needs no JSON form: its
		 * plain text is already what a JSON encoder writes.
		 */
		if (!(value instanceof Boolean || value instanceof Number || value instanceof Map<?, ?>
				|| (value instanceof Iterable<?> && !(value instanceof java.nio.file.Path))
				|| value.getClass().isArray())) {
			return null;
		}
		StringBuilder sb = new StringBuilder();
		write(sb, value, 0);
		return sb.toString();
	}

	private static void write(StringBuilder sb, @Nullable Object value, int depth) {
		if (value == null) {
			sb.append("null");
		}
		else if (value instanceof Boolean || value instanceof Integer || value instanceof Long || value instanceof Short
				|| value instanceof Byte) {
			sb.append(value);
		}
		else if (value instanceof Double d) {
			writeFloating(sb, d, d.isNaN() || d.isInfinite());
		}
		else if (value instanceof Float f) {
			writeFloating(sb, f, f.isNaN() || f.isInfinite());
		}
		else if (value instanceof Number n) {
			// BigDecimal, BigInteger, atomics: their toString is a valid JSON number.
			sb.append(n);
		}
		else if (depth >= MAX_DEPTH) {
			appendString(sb, String.valueOf(value));
		}
		else if (value instanceof Map<?, ?> m) {
			sb.append('{');
			boolean first = true;
			for (var e : m.entrySet()) {
				if (!first) {
					sb.append(',');
				}
				first = false;
				appendString(sb, String.valueOf(e.getKey()));
				sb.append(':');
				write(sb, e.getValue(), depth + 1);
			}
			sb.append('}');
		}
		else if (value instanceof Iterable<?> it && !(value instanceof java.nio.file.Path)) {
			sb.append('[');
			boolean first = true;
			for (Object o : it) {
				if (!first) {
					sb.append(',');
				}
				first = false;
				write(sb, o, depth + 1);
			}
			sb.append(']');
		}
		else if (value.getClass().isArray()) {
			sb.append('[');
			int length = Array.getLength(value);
			for (int i = 0; i < length; i++) {
				if (i > 0) {
					sb.append(',');
				}
				write(sb, Array.get(value, i), depth + 1);
			}
			sb.append(']');
		}
		else {
			appendString(sb, value.toString());
		}
	}

	private static void writeFloating(StringBuilder sb, Number n, boolean nonFinite) {
		if (nonFinite) {
			appendString(sb, n.toString());
		}
		else {
			sb.append(n);
		}
	}

	/*
	 * Same escaping as the format package's JSON key values formatter.
	 */
	static void appendString(StringBuilder output, String value) {
		output.append('"');
		for (int i = 0; i < value.length(); i++) {
			char c = value.charAt(i);
			switch (c) {
				case '"' -> output.append("\\\"");
				case '\\' -> output.append("\\\\");
				case '\b' -> output.append("\\b");
				case '\f' -> output.append("\\f");
				case '\n' -> output.append("\\n");
				case '\r' -> output.append("\\r");
				case '\t' -> output.append("\\t");
				default -> {
					if (c < 0x20 || c == ' ' || c == ' ') {
						appendUnicodeEscape(output, c);
					}
					else if (Character.isHighSurrogate(c)) {
						if (i + 1 < value.length() && Character.isLowSurrogate(value.charAt(i + 1))) {
							output.append(c).append(value.charAt(++i));
						}
						else {
							appendUnicodeEscape(output, c);
						}
					}
					else if (Character.isLowSurrogate(c)) {
						appendUnicodeEscape(output, c);
					}
					else {
						output.append(c);
					}
				}
			}
		}
		output.append('"');
	}

	private static void appendUnicodeEscape(StringBuilder output, char c) {
		String hex = "0123456789abcdef";
		output.append("\\u")
			.append(hex.charAt((c >>> 12) & 0xf))
			.append(hex.charAt((c >>> 8) & 0xf))
			.append(hex.charAt((c >>> 4) & 0xf))
			.append(hex.charAt(c & 0xf));
	}

}

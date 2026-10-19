package io.jstach.rainbowgum.otlp;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.jspecify.annotations.Nullable;

/*
 * Parses the JSON form of a key value (KeyValues#getJsonOrNull) into what an OTLP AnyValue
 * holds: Map (kvlist), List (array), String, Long (int), Double, Boolean, or null (an
 * empty AnyValue). Integers that do not fit a long become doubles.
 */
final class JsonTree {

	private final String s;

	private int i;

	private JsonTree(String s) {
		this.s = s;
	}

	/*
	 * Throws IllegalArgumentException if json is not exactly one JSON value.
	 */
	static @Nullable Object parse(String json) {
		var p = new JsonTree(json);
		Object v = p.value(0);
		p.skipWhitespace();
		if (p.i != json.length()) {
			throw p.error();
		}
		return v;
	}

	private IllegalArgumentException error() {
		return new IllegalArgumentException("Invalid JSON at " + i);
	}

	private void skipWhitespace() {
		while (i < s.length()) {
			char c = s.charAt(i);
			if (c != ' ' && c != '\t' && c != '\n' && c != '\r') {
				return;
			}
			i++;
		}
	}

	private char next() {
		skipWhitespace();
		if (i >= s.length()) {
			throw error();
		}
		return s.charAt(i);
	}

	private void expect(char c) {
		if (next() != c) {
			throw error();
		}
		i++;
	}

	private @Nullable Object value(int depth) {
		if (depth > 64) {
			throw error();
		}
		char c = next();
		return switch (c) {
			case '{' -> object(depth);
			case '[' -> array(depth);
			case '"' -> string();
			case 't' -> literal("true", true);
			case 'f' -> literal("false", false);
			case 'n' -> literal("null", null);
			default -> number();
		};
	}

	private @Nullable Object literal(String text, @Nullable Object value) {
		if (!s.startsWith(text, i)) {
			throw error();
		}
		i += text.length();
		return value;
	}

	private Map<String, @Nullable Object> object(int depth) {
		i++;
		Map<String, @Nullable Object> map = new LinkedHashMap<>();
		if (next() == '}') {
			i++;
			return map;
		}
		while (true) {
			if (next() != '"') {
				throw error();
			}
			String key = string();
			expect(':');
			map.put(key, value(depth + 1));
			char c = next();
			i++;
			if (c == '}') {
				return map;
			}
			if (c != ',') {
				throw error();
			}
		}
	}

	private List<@Nullable Object> array(int depth) {
		i++;
		List<@Nullable Object> list = new ArrayList<>();
		if (next() == ']') {
			i++;
			return list;
		}
		while (true) {
			list.add(value(depth + 1));
			char c = next();
			i++;
			if (c == ']') {
				return list;
			}
			if (c != ',') {
				throw error();
			}
		}
	}

	private String string() {
		i++;
		var sb = new StringBuilder();
		while (i < s.length()) {
			char c = s.charAt(i++);
			if (c == '"') {
				return sb.toString();
			}
			if (c != '\\') {
				sb.append(c);
				continue;
			}
			if (i >= s.length()) {
				break;
			}
			char e = s.charAt(i++);
			switch (e) {
				case '"', '\\', '/' -> sb.append(e);
				case 'b' -> sb.append('\b');
				case 'f' -> sb.append('\f');
				case 'n' -> sb.append('\n');
				case 'r' -> sb.append('\r');
				case 't' -> sb.append('\t');
				case 'u' -> {
					if (i + 4 > s.length()) {
						throw error();
					}
					try {
						sb.append((char) Integer.parseInt(s, i, i + 4, 16));
					}
					catch (NumberFormatException ex) {
						throw error();
					}
					i += 4;
				}
				default -> throw error();
			}
		}
		throw error();
	}

	private Object number() {
		int start = i;
		boolean integer = true;
		while (i < s.length()) {
			char c = s.charAt(i);
			if (c == '.' || c == 'e' || c == 'E') {
				integer = false;
			}
			else if (!(c == '-' || c == '+' || (c >= '0' && c <= '9'))) {
				break;
			}
			i++;
		}
		String text = s.substring(start, i);
		if (text.isEmpty()) {
			throw error();
		}
		try {
			if (integer) {
				try {
					return Long.parseLong(text);
				}
				catch (NumberFormatException tooBig) {
					// fall through to double
				}
			}
			double d = Double.parseDouble(text);
			if (Double.isNaN(d) || Double.isInfinite(d)) {
				throw error();
			}
			return d;
		}
		catch (NumberFormatException e) {
			throw error();
		}
	}

}

package io.jstach.rainbowgum.format;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;

import org.jspecify.annotations.Nullable;

import io.jstach.rainbowgum.KeyValues;
import io.jstach.rainbowgum.KeyValues.KeyValuesConsumer;
import io.jstach.rainbowgum.LogEvent;
import io.jstach.rainbowgum.LogFormatter;
import io.jstach.rainbowgum.LogFormatter.KeyValueNullStrategy;
import io.jstach.rainbowgum.annotation.CaseChanging;

/**
 * Builds a {@link LogFormatter} that appends event key values percent encoded, or as
 * JSON, JSON5, or logfmt text, directly to a {@link StringBuilder}. The default writes
 * all keys percent encoded, preserving their order, which reads back exactly with
 * {@link io.jstach.rainbowgum.PercentCodec#decode(CharSequence, java.nio.charset.Charset)}.
 * In JSON and JSON5 non-null values are always strings, even when they look like numbers,
 * booleans, or JSON.
 * <p>
 * Compose it with other formatters through
 * {@link LogFormatter.Builder#keyValues(java.util.function.Consumer)}, or
 * {@link LogFormatter.Builder#add(LogFormatter)} with a built formatter:
 * {@snippet :
 * LogFormatter formatter = LogFormatter.builder()
 * 	.message()
 * 	.text(" ")
 * 	.add(new KeyValuesFormatterBuilder().format(KeyValuesFormatterBuilder.Format.JSON5)
 * 		.nullStrategy(LogFormatter.KeyValueNullStrategy.SKIP)
 * 		.build())
 * 	.newline()
 * 	.build();
 * }
 * <p>
 * JSON and JSON5 results include braces, including {@code {}} when no keys are written;
 * percent encoding and logfmt have no braces and write nothing when no keys are written.
 * The builder is mutable; each built formatter is immutable and thread-safe.
 */
public final class KeyValuesFormatterBuilder {

	/**
	 * Syntax of the key values object.
	 */
	@CaseChanging
	public enum Format {

		/**
		 * Percent encoded like a URI query, the default:
		 * <code>requestId=42&amp;user=Ada%20Lovelace</code>. Keys and values are encoded
		 * with {@link io.jstach.rainbowgum.PercentCodec}, so every key value is
		 * reversible and on one line.
		 */
		PERCENT,
		/**
		 * JSON, with every key enclosed in double quotes.
		 */
		JSON,
		/**
		 * JSON5, with identifier keys left unquoted. Other keys and all string values
		 * still use JSON double quotes and escaping. Identifier keys follow the
		 * <a href="https://spec.json5.org/#objects">JSON5 object syntax</a>.
		 */
		JSON5,
		/**
		 * <a href="https://brandur.org/logfmt">logfmt</a>: space separated
		 * <code>key=value</code> pairs, <code>requestId=42 user="Ada Lovelace"</code>. A
		 * value is quoted only when it is empty or contains a space, <code>=</code>,
		 * <code>"</code>, or a control character, and characters logfmt does not allow in
		 * keys are replaced with <code>_</code>.
		 */
		LOGFMT;

	}

	private @Nullable List<String> keys;

	private KeyValueNullStrategy nullStrategy = KeyValueNullStrategy.KEEP;

	private Format format = Format.PERCENT;

	/**
	 * Creates a builder that writes all event key values percent encoded.
	 */
	public KeyValuesFormatterBuilder() {
	}

	/**
	 * Selects the keys to write, in the supplied order. The list is copied and duplicate
	 * keys are written once at their first position. An empty list writes {@code {}}.
	 * Missing selected keys follow the null strategy, just like keys mapped to null.
	 * @param keys keys to select.
	 * @return this builder.
	 */
	public KeyValuesFormatterBuilder keys(List<String> keys) {
		this.keys = List.copyOf(new LinkedHashSet<>(keys));
		return this;
	}

	/**
	 * Selects all keys, in the order supplied by the event's key values. This is the
	 * default and resets a previous {@link #keys(List)} selection.
	 * @return this builder.
	 */
	public KeyValuesFormatterBuilder allKeys() {
		this.keys = null;
		return this;
	}

	/**
	 * Controls null values, and missing keys when a selection is supplied:
	 * {@link KeyValueNullStrategy#KEEP} writes {@code null} (logfmt: <code>key=</code>,
	 * percent encoding: the key alone), {@link KeyValueNullStrategy#EMPTY} writes an
	 * empty string, and {@link KeyValueNullStrategy#SKIP} omits the key. The default is
	 * KEEP.
	 * @param nullStrategy null value strategy.
	 * @return this builder.
	 */
	public KeyValuesFormatterBuilder nullStrategy(KeyValueNullStrategy nullStrategy) {
		this.nullStrategy = Objects.requireNonNull(nullStrategy);
		return this;
	}

	/**
	 * Selects the syntax. The default is {@link Format#PERCENT}.
	 * @param format syntax to write.
	 * @return this builder.
	 */
	public KeyValuesFormatterBuilder format(Format format) {
		this.format = Objects.requireNonNull(format);
		return this;
	}

	/**
	 * Builds an immutable formatter independent of subsequent builder changes.
	 * @return key values formatter.
	 */
	public LogFormatter build() {
		return build(true);
	}

	/* TTLL supplies the braces and suppresses empty key values. */
	LogFormatter buildUnbraced() {
		return build(false);
	}

	private LogFormatter build(boolean braces) {
		return switch (format) {
			case JSON, JSON5 -> new JsonKeyValuesFormatter(keys, nullStrategy, format, braces);
			case PERCENT -> new PercentKeyValuesFormatter(keys, nullStrategy);
			case LOGFMT -> new LogfmtKeyValuesFormatter(keys, nullStrategy);
		};
	}

}

record JsonKeyValuesFormatter(@Nullable List<String> keys, KeyValueNullStrategy nullStrategy,
		KeyValuesFormatterBuilder.Format syntax,
		boolean braces) implements LogFormatter.EventFormatter, KeyValuesConsumer<StringBuilder> {

	@Override
	public void format(StringBuilder output, LogEvent event) {
		if (braces) {
			output.append('{');
		}
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
		if (braces) {
			output.append('}');
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
				}
			}
		}
		if (count > 0) {
			output.append(',');
		}
		if (syntax == KeyValuesFormatterBuilder.Format.JSON5 && isIdentifier(key)) {
			output.append(key);
		}
		else {
			appendString(output, key);
		}
		output.append(':');
		if (value == null) {
			output.append("null");
		}
		else {
			appendString(output, value);
		}
		return count + 1;
	}

	/* JSON5 uses ECMAScript IdentifierName, not Java identifier rules. */
	static boolean isIdentifier(String key) {
		if (key.isEmpty()) {
			return false;
		}
		int first = key.codePointAt(0);
		if (!isIdentifierStart(first)) {
			return false;
		}
		for (int i = Character.charCount(first); i < key.length();) {
			int c = key.codePointAt(i);
			if (!isIdentifierStart(c) && c != 0x200c && c != 0x200d) {
				boolean part = switch (Character.getType(c)) {
					case Character.NON_SPACING_MARK, Character.COMBINING_SPACING_MARK, Character.DECIMAL_DIGIT_NUMBER,
							Character.CONNECTOR_PUNCTUATION ->
						true;
					default -> false;
				};
				if (!part) {
					return false;
				}
			}
			i += Character.charCount(c);
		}
		return true;
	}

	private static boolean isIdentifierStart(int c) {
		return c == '$' || c == '_' || switch (Character.getType(c)) {
			case Character.UPPERCASE_LETTER, Character.LOWERCASE_LETTER, Character.TITLECASE_LETTER,
					Character.MODIFIER_LETTER, Character.OTHER_LETTER, Character.LETTER_NUMBER ->
				true;
			default -> false;
		};
	}

	/*
	 * Quotes and escapes, as a JSON string, the text from start to the end of the output.
	 * Text needing no escapes, the common case, is only wrapped in quotes.
	 */
	static void quoteInPlace(StringBuilder output, int start) {
		int end = output.length();
		for (int i = start; i < end; i++) {
			char c = output.charAt(i);
			if (c == '"' || c == '\\' || c < 0x20 || c == '\u2028' || c == '\u2029' || Character.isSurrogate(c)) {
				String raw = output.substring(start);
				output.setLength(start);
				appendString(output, raw);
				return;
			}
		}
		output.insert(start, '"');
		output.append('"');
	}

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
					if (c < 0x20 || c == '\u2028' || c == '\u2029') {
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

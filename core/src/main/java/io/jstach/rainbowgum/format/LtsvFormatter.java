package io.jstach.rainbowgum.format;

import java.nio.charset.StandardCharsets;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import org.jspecify.annotations.Nullable;

import io.jstach.rainbowgum.KeyValues.KeyValuesConsumer;
import io.jstach.rainbowgum.LogEvent;
import io.jstach.rainbowgum.LogFormatter;
import io.jstach.rainbowgum.LogFormatter.ThrowableFormatter;
import io.jstach.rainbowgum.LogProperties;
import io.jstach.rainbowgum.ServiceRegistry;
import io.jstach.rainbowgum.annotation.LogConfigurable;

/*
 * LTSV (http://ltsv.org), Labeled Tab-separated Values. Package private: the public API is
 * the generated LtsvFormatterBuilder and the ltsv encoder scheme.
 */
final class LtsvFormatter implements LogFormatter.EventFormatter {

	/** {@link io.jstach.rainbowgum.format.TTLL.TimestampFormat#ISO iso} */
	static final LogFormatter DEFAULT_TIMESTAMP = TTLL.TimestampFormat.ISO.formatter();

	/** {@link io.jstach.rainbowgum.format.TTLL.LevelFormat#PLAIN plain} */
	static final LogFormatter DEFAULT_LEVEL = TTLL.LevelFormat.PLAIN.formatter();

	/** {@link io.jstach.rainbowgum.format.TTLL.LoggerFormat#FULL full} */
	static final LogFormatter DEFAULT_LOGGER = TTLL.LoggerFormat.FULL.formatter();

	/** none, so every field is labeled */
	static final List<EventField> DEFAULT_COLUMNS = List.of();

	private final EventField[] columns;

	/*
	 * The fields not in columns, in the order of EventField.
	 */
	private final EventField[] labeled;

	private final LogFormatter timestamp;

	private final ThreadFields thread;

	private final LogFormatter level;

	private final LogFormatter logger;

	private final Palette palette;

	/*
	 * A key value named like a field written here gets a keyValues. prefix, as in the
	 * logfmt and JSON encoders, so no label is written twice.
	 */
	private final KeyValuesConsumer<StringBuilder> keyValues;

	private LtsvFormatter(List<EventField> columns, LogFormatter timestamp, ThreadFields thread, LogFormatter level,
			LogFormatter logger, Palette palette) {
		this.columns = columns.toArray(EventField[]::new);
		this.labeled = EnumSet
			.complementOf(columns.isEmpty() ? EnumSet.noneOf(EventField.class) : EnumSet.copyOf(columns))
			.toArray(EventField[]::new);
		this.timestamp = timestamp;
		this.thread = thread;
		this.level = level;
		this.logger = logger;
		this.palette = palette;
		Set<String> fields = Json5Formatter.fields(thread);
		this.keyValues = (kvs, key, value, count, output) -> appendKeyValue(
				collides(fields, key) ? Json5Formatter.KEY_VALUES_FIELD + "." + key : key, value, count, output);
	}

	/**
	 * Formats events as <a href="http://ltsv.org">LTSV</a> (Labeled Tab-separated
	 * Values), one line of tab separated <code>label:value</code> fields per event,
	 * selected with the ltsv encoder scheme.
	 * <p>
	 * Each event is written as one line, for example (<code>⇥</code> is a tab):
	 * <code>time:2026-10-05T15:04:05.123Z⇥level:INFO⇥logger:com.example.App⇥threadId:1⇥threadName:main⇥message:hello world⇥requestId:42</code>
	 * <ul>
	 * <li>The labels are logfmt's field names. The thread fields are chosen with
	 * {@link ThreadFields}, by default <code>threadId</code> and
	 * <code>threadName</code>.</li>
	 * <li>The fields listed as columns (see {@link EventField}) come first, in the order
	 * listed and without labels, so they are read by position. With
	 * <code>columns=time,level,logger,thread,message</code>:
	 * <code>2026-10-05T15:04:05.123Z⇥INFO⇥com.example.App⇥1⇥main⇥hello world⇥requestId:42</code>.
	 * The fields not listed follow with their labels, before the key values. With
	 * <code>columns=thread,message</code>:
	 * <code>1⇥main⇥hello world⇥time:2026-10-05T15:04:05.123Z⇥level:INFO⇥logger:com.example.App⇥requestId:42</code>.
	 * Output with columns is no longer LTSV.</li>
	 * <li><code>time</code> is the UTC instant with milliseconds by default, and
	 * <code>timestamp</code> chooses another format or a DateTimeFormatter pattern in
	 * UTC, as for TTLL.</li>
	 * <li><code>level</code> is <code>TRACE</code>, <code>DEBUG</code>,
	 * <code>INFO</code>, <code>WARN</code>, or <code>ERROR</code>, unpadded by
	 * default.</li>
	 * <li>A field set to <code>none</code> is not written, and cannot be a column.</li>
	 * <li>The key values follow the fields, in order. A key value named like one of these
	 * fields is prefixed with <code>keyValues.</code>. A key value without a value is
	 * written with an empty one.</li>
	 * <li>A throwable adds <code>error</code> (its <code>toString()</code>) and
	 * <code>stacktrace</code>.</li>
	 * </ul>
	 * <strong>Value escaping.</strong> Values are never quoted. The same escaping applies
	 * to messages, metadata, key values, and throwable text:
	 * <table>
	 * <caption>LTSV value escapes</caption>
	 * <tr>
	 * <th scope="col">Input</th>
	 * <th scope="col">Written text</th>
	 * </tr>
	 * <tr>
	 * <td>Backslash</td>
	 * <td><code>\\</code></td>
	 * </tr>
	 * <tr>
	 * <td>Tab, LF, CR</td>
	 * <td><code>\t</code>, <code>\n</code>, <code>\r</code></td>
	 * </tr>
	 * <tr>
	 * <td>Other C0 controls (U+0000–U+001F), DEL and C1 (U+007F–U+009F), U+2028/U+2029,
	 * Unicode format characters (category Cf), and unpaired UTF-16 surrogates</td>
	 * <td><code>&#92;uXXXX</code>, four lowercase hexadecimal digits per UTF-16 code
	 * unit</td>
	 * </tr>
	 * </table>
	 * This covers the Unicode 17 format characters even on Java 21, including bidi
	 * controls. A supplementary format character becomes two surrogate escapes. Other
	 * valid Unicode text is preserved without normalization, including ordinary
	 * supplementary characters. Quotes, colons, and percent signs in values are
	 * unchanged. Every ESC (U+001B) and C1 control from input is escaped, including SGR
	 * sequences that could hide or restyle text. An actual newline becomes
	 * <code>\n</code>; a literal backslash followed by n becomes <code>\\n</code>.
	 * <p>
	 * <strong>Label encoding.</strong> Labels preserve <code>A-Z a-z 0-9 _ . -</code>.
	 * Other valid Unicode is encoded as UTF-8 bytes, each written as <code>%HH</code>
	 * with uppercase hexadecimal digits: <code>a b</code> becomes <code>a%20b</code>,
	 * <code>:</code> becomes <code>%3A</code>, and a literal <code>%</code> becomes
	 * <code>%25</code>. An empty key becomes a lone <code>%</code>. An unpaired surrogate
	 * uses the extension <code>%uXXXX</code> with four uppercase hexadecimal digits (for
	 * example <code>%uD800</code>), preserving malformed input without collisions. A
	 * literal <code>%uD800</code> becomes <code>%25uD800</code>. Keys starting with
	 * <code>keyValues.</code> also receive that prefix, keeping application keys distinct
	 * from prefixed built-in field names.
	 * <p>
	 * <strong>Reading records.</strong> For machine parsing, use <code>color=off</code>
	 * and leave <code>columns</code> empty. Split at raw LF and tabs, then split each
	 * labeled field at its first colon. Decode value backslash escapes once, after
	 * splitting; recursive decoding would turn literal escape text into controls. Decode
	 * label percent bytes as UTF-8, handling the empty-key sentinel and surrogate
	 * extension separately. These are this formatter's encoding conventions layered on
	 * LTSV; generic LTSV readers need them to recover the original text. Escaping a value
	 * takes linear time and space in its input length; this formatter does not impose a
	 * record or value length limit.
	 * <p>
	 * Values can be colored like the TTLL formatter; built-in labels and
	 * <code>message</code> are not. Key-value labels and values are colored together.
	 * <code>error</code> and <code>stacktrace</code> take the level's color, since they
	 * are on the event's line. These formatter-owned colors are added outside value
	 * escaping; escape characters supplied in field values are always escaped. Select
	 * <code>color=off</code> to suppress formatter-owned ANSI sequences as well.
	 * @param name encoder name, used for property lookup.
	 * @param timestamp time format, see {@link TTLL.TimestampFormat};
	 * <code>default</code> is <code>iso</code>.
	 * @param thread which thread fields, see {@link ThreadFields}.
	 * @param level level format, see {@link TTLL.LevelFormat}; <code>default</code> is
	 * <code>plain</code>.
	 * @param logger logger name format, see {@link TTLL.LoggerFormat}.
	 * @param columns the fields written first without labels, see {@link EventField}.
	 * @param color whether to color values, see {@link TTLL.ColorMode}.
	 * @param theme which colors, see {@link TTLL.ColorTheme}.
	 * @param serviceRegistry where themes chosen by name in properties are looked up.
	 * @return formatter.
	 */
	@LogConfigurable(name = "LtsvFormatterBuilder", prefix = LogProperties.ENCODER_PREFIX + "ltsv.")
	static LogFormatter of(@LogConfigurable.KeyParameter String name,
			@LogConfigurable.DefaultParameter("DEFAULT_TIMESTAMP") @LogConfigurable.ConvertParameter("convertTimestamp") LogFormatter timestamp,
			@LogConfigurable.DefaultParameter(
					constant = "BOTH") @LogConfigurable.ConvertParameter("convertThread") ThreadFields thread,
			@LogConfigurable.DefaultParameter("DEFAULT_LEVEL") @LogConfigurable.ConvertParameter("convertLevel") LogFormatter level,
			@LogConfigurable.DefaultParameter("DEFAULT_LOGGER") @LogConfigurable.ConvertParameter("convertLogger") LogFormatter logger,
			@LogConfigurable.DefaultParameter("DEFAULT_COLUMNS") @LogConfigurable.ConvertParameter("convertColumns") List<EventField> columns,
			@LogConfigurable.ConvertParameter("convertColor") TTLL.@Nullable ColorMode color,
			@LogConfigurable.ConvertParameter("convertTheme") TTLL.@Nullable ColorTheme theme,
			@LogConfigurable.PassThroughParameter @Nullable ServiceRegistry serviceRegistry) {
		checkColumns(columns);
		for (var f : columns) {
			boolean none = switch (f) {
				case TIME -> timestamp.isNoop();
				case LEVEL -> level.isNoop();
				case LOGGER -> logger.isNoop();
				case THREAD -> thread == ThreadFields.NONE;
				case MESSAGE -> false;
			};
			if (none) {
				String field = f.name().toLowerCase(Locale.ROOT);
				throw new IllegalArgumentException(
						"columns lists " + field + " but " + (f == EventField.TIME ? "timestamp" : field) + " is none");
			}
		}
		return new LtsvFormatter(columns, timestamp, thread, level, logger,
				Palette.of(color == null ? TTLL.ColorMode.DEFAULT : color,
						PaletteColorTheme.resolve(theme, serviceRegistry), AnsiSupport::isAnsiSupported));
	}

	static LogFormatter convertTimestamp(String value) {
		return isDefault(value) ? DEFAULT_TIMESTAMP : TTLLFormatter.convertTimestamp(value);
	}

	static ThreadFields convertThread(String value) {
		return ThreadFields.parse(value);
	}

	static LogFormatter convertLevel(String value) {
		return isDefault(value) ? DEFAULT_LEVEL : TTLLFormatter.convertLevel(value);
	}

	static LogFormatter convertLogger(String value) {
		return TTLLFormatter.convertLogger(value);
	}

	/*
	 * The TTLL enums' default and true are TTLL's defaults, which differ for time and
	 * level.
	 */
	private static boolean isDefault(String value) {
		String v = value.strip().toLowerCase(Locale.ROOT);
		return v.equals("default") || v.equals("true");
	}

	static List<EventField> convertColumns(String value) {
		return checkColumns(LogProperties.parseList(value).stream().map(EventField::parse).toList());
	}

	private static List<EventField> checkColumns(List<EventField> columns) {
		var seen = EnumSet.noneOf(EventField.class);
		for (var f : columns) {
			if (!seen.add(f)) {
				throw new IllegalArgumentException("columns lists " + f.name().toLowerCase(Locale.ROOT)
						+ " more than once: " + columns.stream().map(c -> c.name().toLowerCase(Locale.ROOT)).toList());
			}
		}
		return columns;
	}

	static TTLL.ColorMode convertColor(String value) {
		return TTLLFormatter.convertColor(value);
	}

	static TTLL.ColorTheme convertTheme(String value) {
		return TTLLFormatter.convertTheme(value);
	}

	private static boolean collides(Set<String> fields, String key) {
		return fields.contains(key) || key.startsWith(Json5Formatter.KEY_VALUES_FIELD + ".");
	}

	@Override
	public void format(StringBuilder output, LogEvent event) {
		String levelColor = palette.levelColor(event.level());
		boolean any = false;
		for (var f : columns) {
			any = appendField(output, any, f, false, event, levelColor);
		}
		for (var f : labeled) {
			any = appendField(output, any, f, true, event, levelColor);
		}
		var keyValues = event.keyValues();
		if (!keyValues.isEmpty()) {
			any = separate(output, any);
			Palette.start(output, palette.keyValues());
			keyValues.forEach(this.keyValues, 0, output);
			Palette.end(output, palette.keyValues());
		}
		var throwable = event.throwableOrNull();
		if (throwable != null) {
			separate(output, any);
			output.append("error:");
			Palette.start(output, levelColor);
			appendValue(output, throwable.toString());
			Palette.end(output, levelColor);
			output.append("\tstacktrace:");
			Palette.start(output, levelColor);
			int start = output.length();
			ThrowableFormatter.appendThrowable(output, throwable);
			trimTrailingLineBreaks(output, start);
			escapeInPlace(output, start);
			Palette.end(output, levelColor);
		}
		output.append('\n');
	}

	private boolean appendField(StringBuilder output, boolean any, EventField field, boolean labeled, LogEvent event,
			String levelColor) {
		var p = palette;
		switch (field) {
			case TIME -> {
				return appendFormatted(output, any, labeled, "time:", p.timestamp(), timestamp, event);
			}
			case LEVEL -> {
				return appendFormatted(output, any, labeled, "level:", levelColor, level, event);
			}
			case LOGGER -> {
				return appendFormatted(output, any, labeled, "logger:", p.logger(), logger, event);
			}
			case THREAD -> {
				return switch (thread) {
					case NAME -> appendThreadName(output, any, labeled, "thread:", event);
					case ID -> appendThreadId(output, any, labeled, event);
					case BOTH -> appendThreadName(output, appendThreadId(output, any, labeled, event), labeled,
							"threadName:", event);
					case NONE -> any;
				};
			}
			case MESSAGE -> {
				separate(output, any);
				label(output, labeled, "message:");
				int start = output.length();
				event.formattedMessage(output);
				escapeInPlace(output, start);
			}
		}
		return true;
	}

	/*
	 * A tab before every field but the first, even when the first wrote an empty value,
	 * so columns stay in place.
	 */
	private static boolean separate(StringBuilder output, boolean any) {
		if (any) {
			output.append('\t');
		}
		return true;
	}

	private static boolean appendFormatted(StringBuilder output, boolean any, boolean labeled, String label,
			String color, LogFormatter formatter, LogEvent event) {
		if (formatter.isNoop()) {
			return any;
		}
		separate(output, any);
		label(output, labeled, label);
		Palette.start(output, color);
		int start = output.length();
		formatter.format(output, event);
		escapeInPlace(output, start);
		Palette.end(output, color);
		return true;
	}

	private static void label(StringBuilder output, boolean labeled, String label) {
		if (labeled) {
			output.append(label);
		}
	}

	private boolean appendThreadName(StringBuilder output, boolean any, boolean labeled, String label, LogEvent event) {
		separate(output, any);
		label(output, labeled, label);
		Palette.start(output, palette.thread());
		appendValue(output, event.threadName());
		Palette.end(output, palette.thread());
		return true;
	}

	private boolean appendThreadId(StringBuilder output, boolean any, boolean labeled, LogEvent event) {
		separate(output, any);
		label(output, labeled, "threadId:");
		Palette.start(output, palette.thread());
		output.append(event.threadId());
		Palette.end(output, palette.thread());
		return true;
	}

	private static int appendKeyValue(String key, @Nullable String value, int count, StringBuilder output) {
		if (count > 0) {
			output.append('\t');
		}
		appendLabel(output, key);
		output.append(':');
		if (value != null) {
			appendValue(output, value);
		}
		return count + 1;
	}

	private static void appendValue(StringBuilder output, String value) {
		int start = output.length();
		output.append(value);
		escapeInPlace(output, start);
	}

	/*
	 * Percent-encodes, as UTF-8, the characters LTSV labels do not allow, and % itself,
	 * so two different keys never write the same label. The empty key is a lone %, which
	 * no encoded non-empty key can be.
	 */
	static void appendLabel(StringBuilder output, String key) {
		if (key.isEmpty()) {
			output.append('%');
			return;
		}
		for (int i = 0; i < key.length(); i++) {
			char c = key.charAt(i);
			if (isLabelChar(c)) {
				output.append(c);
				continue;
			}
			int end = Character.isHighSurrogate(c) && i + 1 < key.length()
					&& Character.isLowSurrogate(key.charAt(i + 1)) ? i + 2 : i + 1;
			if (end == i + 1 && Character.isSurrogate(c)) {
				output.append("%u");
				appendHex(output, c, HEX);
			}
			else {
				for (byte b : key.substring(i, end).getBytes(StandardCharsets.UTF_8)) {
					output.append('%').append(HEX[(b >> 4) & 0xF]).append(HEX[b & 0xF]);
				}
			}
			i = end - 1;
		}
	}

	private static final char[] HEX = "0123456789ABCDEF".toCharArray();

	private static final char[] ESCAPE_HEX = "0123456789abcdef".toCharArray();

	private static boolean isLabelChar(char c) {
		return (c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z') || (c >= '0' && c <= '9') || c == '_' || c == '.'
				|| c == '-';
	}

	/*
	 * Backslash escapes the text from start to the end of the output if it has a control
	 * character or backslash, in place so the common case copies nothing. Replacing each
	 * escaped character shifts the rest of the text, which is quadratic for a message of
	 * many lines, so from the first escape on the escaped copy is appended after the text
	 * in one pass and the original is then deleted. Each value character produces at most
	 * six characters, so time and extra space remain linear.
	 */
	static void escapeInPlace(StringBuilder output, int start) {
		int end = output.length();
		int first = start;
		while (first < end) {
			char c = output.charAt(first);
			if (!isMarked(c)) {
				first++;
			}
			else if (isUnescapedPair(output, first, end)) {
				first += 2;
			}
			else {
				break;
			}
		}
		if (first == end) {
			return;
		}
		for (int i = first; i < end; i++) {
			char c = output.charAt(i);
			if (!isMarked(c)) {
				output.append(c);
				continue;
			}
			switch (c) {
				case '\\' -> output.append("\\\\");
				case '\t' -> output.append("\\t");
				case '\n' -> output.append("\\n");
				case '\r' -> output.append("\\r");
				default -> {
					if (isUnescapedPair(output, i, end)) {
						output.append(c).append(output.charAt(++i));
					}
					else {
						// A lone surrogate, or each half of a supplementary format
						// character.
						appendUnicodeEscape(output, c);
					}
				}
			}
		}
		output.delete(first, end);
	}

	private static boolean isMarked(char c) {
		return c == '\\' || c < ' ' || (c >= 0x7f && Marked.isMarked(c));
	}

	/*
	 * One bit per UTF-16 code unit from DEL on that may need escaping, so non-ASCII text
	 * costs an array read rather than a Unicode table lookup. Every surrogate is marked;
	 * a valid pair is then checked as a code point. Built on first use, so ASCII-only
	 * output never builds it.
	 */
	private static final class Marked {

		private static final long[] BITS = bits();

		private static long[] bits() {
			long[] bits = new long[(Character.MAX_VALUE + 1) >>> 6];
			for (int c = 0x7f; c <= Character.MAX_VALUE; c++) {
				if (isEscapedControl(c)) {
					bits[c >>> 6] |= 1L << c;
				}
			}
			return bits;
		}

		static boolean isMarked(char c) {
			return (BITS[c >>> 6] & (1L << c)) != 0;
		}

	}

	// A valid surrogate pair whose code point is not a format character.
	private static boolean isUnescapedPair(StringBuilder output, int index, int end) {
		char high = output.charAt(index);
		if (!Character.isHighSurrogate(high) || index + 1 >= end) {
			return false;
		}
		char low = output.charAt(index + 1);
		return Character.isLowSurrogate(low) && !isEscapedControl(Character.toCodePoint(high, low));
	}

	private static boolean isEscapedControl(int c) {
		return c < ' ' || (c >= 0x7f && (c <= 0x9f || c == 0x2028 || c == 0x2029
				|| (c >= Character.MIN_SURROGATE && c <= Character.MAX_SURROGATE)
				|| Character.getType(c) == Character.FORMAT
				// Cover Unicode 17's format set with older runtime character databases
				// too.
				|| c == 0x890 || c == 0x891 || (c >= 0x13439 && c <= 0x1343f)));
	}

	private static void appendUnicodeEscape(StringBuilder output, char c) {
		output.append("\\u");
		appendHex(output, c, ESCAPE_HEX);
	}

	private static void appendHex(StringBuilder output, char c, char[] digits) {
		output.append(digits[(c >>> 12) & 0xf])
			.append(digits[(c >>> 8) & 0xf])
			.append(digits[(c >>> 4) & 0xf])
			.append(digits[c & 0xf]);
	}

	private static void trimTrailingLineBreaks(StringBuilder sb, int start) {
		int end = sb.length();
		while (end > start && (sb.charAt(end - 1) == '\n' || sb.charAt(end - 1) == '\r')) {
			end--;
		}
		sb.setLength(end);
	}

}

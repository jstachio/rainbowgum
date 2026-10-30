package io.jstach.rainbowgum.format;

import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Set;

import org.jspecify.annotations.Nullable;

import io.jstach.rainbowgum.KeyValues.KeyValuesConsumer;
import io.jstach.rainbowgum.LogEvent;
import io.jstach.rainbowgum.LogFormatter;
import io.jstach.rainbowgum.LogFormatter.LevelFormatter;
import io.jstach.rainbowgum.LogFormatter.ThrowableFormatter;
import io.jstach.rainbowgum.LogFormatter.TimestampFormatter;
import io.jstach.rainbowgum.LogProperties;
import io.jstach.rainbowgum.ServiceRegistry;
import io.jstach.rainbowgum.annotation.LogConfigurable;

/*
 * LTSV (http://ltsv.org), Labeled Tab-separated Values. Package private: the public API is
 * the generated LtsvFormatterBuilder and the ltsv encoder scheme.
 */
final class LtsvFormatter implements LogFormatter.EventFormatter {

	/*
	 * Fixed width UTC milliseconds, cached per millisecond.
	 */
	private static final TimestampFormatter TIME = TimestampFormatter.ofISO();

	private static final LevelFormatter LEVEL = LevelFormatter.of();

	private final ThreadFields thread;

	private final LtsvLabels labels;

	private final Palette palette;

	/*
	 * A key value named like a field written here gets a keyValues. prefix, as in the
	 * logfmt and JSON encoders, so no label is written twice.
	 */
	private final KeyValuesConsumer<StringBuilder> keyValues;

	private LtsvFormatter(ThreadFields thread, LtsvLabels labels, Palette palette) {
		this.thread = thread;
		this.labels = labels;
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
	 * <code>time:2026-10-05T15:04:05.123Z⇥level:INFO⇥logger:com.example.App⇥threadId:1⇥threadName:main⇥msg:hello world⇥requestId:42</code>
	 * <ul>
	 * <li>The labels are logfmt's field names. The thread fields are chosen with
	 * {@link ThreadFields}, by default <code>threadId</code> and
	 * <code>threadName</code>.</li>
	 * <li>{@link LtsvLabels#KEY_VALUES} leaves the labels off the fields every event has,
	 * so they are read by position:
	 * <code>2026-10-05T15:04:05.123Z⇥INFO⇥com.example.App⇥1⇥main⇥hello world⇥requestId:42</code>.</li>
	 * <li><code>time</code> is the UTC instant with milliseconds.</li>
	 * <li><code>level</code> is <code>TRACE</code>, <code>DEBUG</code>,
	 * <code>INFO</code>, <code>WARN</code>, or <code>ERROR</code>.</li>
	 * <li>Every key value follows <code>msg</code>, in order. A key value named like one
	 * of these fields is prefixed with <code>keyValues.</code>. A key value without a
	 * value is written with an empty one.</li>
	 * <li>A throwable adds <code>error</code> (its <code>toString()</code>) and
	 * <code>stacktrace</code>.</li>
	 * </ul>
	 * Values are never quoted. LTSV does not allow tab, newline, or carriage return in a
	 * value, so these and <code>\</code> are backslash escaped (<code>\t</code>,
	 * <code>\n</code>, <code>\r</code>, <code>\\</code>) and other control characters are
	 * written as <code>\</code><code>uXXXX</code>, so every event stays on one line.
	 * Characters LTSV does not allow in labels (anything but
	 * <code>A-Z a-z 0-9 _ . -</code>) are percent-encoded as UTF-8 (<code>a b</code> is
	 * <code>a%20b</code>), and an empty key is <code>%</code>, so different keys stay
	 * different.
	 * <p>
	 * Values can be colored like the TTLL formatter; labels and <code>msg</code> are not.
	 * <code>error</code> and <code>stacktrace</code> take the level's color, since they
	 * are on the event's line.
	 * @param name encoder name, used for property lookup.
	 * @param thread which thread fields, see {@link ThreadFields}.
	 * @param labels which fields have labels, see {@link LtsvLabels}.
	 * @param color whether to color values, see {@link TTLL.ColorMode}.
	 * @param theme which colors, see {@link TTLL.ColorTheme}.
	 * @param serviceRegistry where themes chosen by name in properties are looked up.
	 * @return formatter.
	 */
	@LogConfigurable(name = "LtsvFormatterBuilder", prefix = LogProperties.ENCODER_PREFIX + "ltsv.")
	static LogFormatter of(@LogConfigurable.KeyParameter String name,
			@LogConfigurable.DefaultParameter(
					constant = "BOTH") @LogConfigurable.ConvertParameter("convertThread") ThreadFields thread,
			@LogConfigurable.DefaultParameter(
					constant = "ALL") @LogConfigurable.ConvertParameter("convertLabels") LtsvLabels labels,
			@LogConfigurable.ConvertParameter("convertColor") TTLL.@Nullable ColorMode color,
			@LogConfigurable.ConvertParameter("convertTheme") TTLL.@Nullable ColorTheme theme,
			@LogConfigurable.PassThroughParameter @Nullable ServiceRegistry serviceRegistry) {
		return new LtsvFormatter(thread, labels, Palette.of(color == null ? TTLL.ColorMode.DEFAULT : color,
				PaletteColorTheme.resolve(theme, serviceRegistry), AnsiSupport::isAnsiSupported));
	}

	static ThreadFields convertThread(String value) {
		return ThreadFields.parse(value);
	}

	static LtsvLabels convertLabels(String value) {
		return LtsvLabels.parse(value);
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
		var p = palette;
		field(output, "time:");
		Palette.start(output, p.timestamp());
		TIME.formatTimestamp(output, event.timestamp());
		Palette.end(output, p.timestamp());
		output.append('\t');
		field(output, "level:");
		String levelColor = p.levelColor(event.level());
		Palette.start(output, levelColor);
		LEVEL.formatLevel(output, event.level());
		Palette.end(output, levelColor);
		output.append('\t');
		field(output, "logger:");
		Palette.start(output, p.logger());
		appendValue(output, event.loggerName());
		Palette.end(output, p.logger());
		switch (thread) {
			case NAME -> appendThreadName(output, "thread:", event);
			case ID -> appendThreadId(output, event);
			case BOTH -> {
				appendThreadId(output, event);
				appendThreadName(output, "threadName:", event);
			}
			case NONE -> {
			}
		}
		output.append('\t');
		field(output, "msg:");
		int start = output.length();
		event.formattedMessage(output);
		escapeInPlace(output, start);
		var keyValues = event.keyValues();
		if (!keyValues.isEmpty()) {
			output.append('\t');
			Palette.start(output, p.keyValues());
			keyValues.forEach(this.keyValues, 0, output);
			Palette.end(output, p.keyValues());
		}
		var throwable = event.throwableOrNull();
		if (throwable != null) {
			output.append("\terror:");
			Palette.start(output, levelColor);
			appendValue(output, throwable.toString());
			Palette.end(output, levelColor);
			output.append("\tstacktrace:");
			Palette.start(output, levelColor);
			start = output.length();
			ThrowableFormatter.appendThrowable(output, throwable);
			trimTrailingLineBreaks(output, start);
			escapeInPlace(output, start);
			Palette.end(output, levelColor);
		}
		output.append('\n');
	}

	private void appendThreadName(StringBuilder output, String label, LogEvent event) {
		output.append('\t');
		field(output, label);
		Palette.start(output, palette.thread());
		appendValue(output, event.threadName());
		Palette.end(output, palette.thread());
	}

	private void appendThreadId(StringBuilder output, LogEvent event) {
		output.append('\t');
		field(output, "threadId:");
		Palette.start(output, palette.thread());
		output.append(event.threadId());
		Palette.end(output, palette.thread());
	}

	/*
	 * The label of a field every event has, unless only key values are labeled.
	 */
	private void field(StringBuilder output, String label) {
		if (labels == LtsvLabels.ALL) {
			output.append(label);
		}
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
			for (byte b : key.substring(i, end).getBytes(StandardCharsets.UTF_8)) {
				output.append('%').append(HEX[(b >> 4) & 0xF]).append(HEX[b & 0xF]);
			}
			i = end - 1;
		}
	}

	private static final char[] HEX = "0123456789ABCDEF".toCharArray();

	private static boolean isLabelChar(char c) {
		return (c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z') || (c >= '0' && c <= '9') || c == '_' || c == '.'
				|| c == '-';
	}

	/*
	 * Backslash escapes the text from start to the end of the output if it has a control
	 * character or backslash, in place so the common case copies nothing.
	 */
	static void escapeInPlace(StringBuilder output, int start) {
		int end = output.length();
		for (int i = start; i < end; i++) {
			String escape = escape(output.charAt(i));
			if (escape != null) {
				output.replace(i, i + 1, escape);
				i += escape.length() - 1;
				end += escape.length() - 1;
			}
		}
	}

	private static @Nullable String escape(char c) {
		return switch (c) {
			case '\\' -> "\\\\";
			case '\t' -> "\\t";
			case '\n' -> "\\n";
			case '\r' -> "\\r";
			default -> c < ' ' || c == 0x7f ? String.format(Locale.ROOT, "\\u%04x", (int) c) : null;
		};
	}

	private static void trimTrailingLineBreaks(StringBuilder sb, int start) {
		int end = sb.length();
		while (end > start && (sb.charAt(end - 1) == '\n' || sb.charAt(end - 1) == '\r')) {
			end--;
		}
		sb.setLength(end);
	}

}

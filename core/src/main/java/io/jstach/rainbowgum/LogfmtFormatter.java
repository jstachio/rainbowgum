package io.jstach.rainbowgum;

import java.util.List;
import java.util.Locale;

import org.jspecify.annotations.Nullable;

import io.jstach.rainbowgum.KeyValues.KeyValuesConsumer;
import io.jstach.rainbowgum.LogFormatter.KeyValueNullStrategy;
import io.jstach.rainbowgum.LogFormatter.LevelFormatter;
import io.jstach.rainbowgum.LogFormatter.ThrowableFormatter;
import io.jstach.rainbowgum.LogFormatter.TimestampFormatter;
import io.jstach.rainbowgum.annotation.LogConfigurable;

/*
 * logfmt (https://brandur.org/logfmt). Package private: the public API is the generated
 * LogfmtFormatterBuilder, the logfmt encoder scheme, and LogFormatter.Builder's
 * logfmtKeyValues methods.
 */
final class LogfmtFormatter implements LogFormatter.EventFormatter {

	/*
	 * The encoder URI scheme.
	 */
	static final String SCHEME = "logfmt";

	static final LevelFormatter DEFAULT_LEVEL_FORMATTER = DefaultLevelFormatter.LEVEL_FORMATTER;

	/*
	 * Fixed width UTC milliseconds, cached per millisecond.
	 */
	private static final TimestampFormatter TIME = TimestampFormatter.ofISO();

	private final LevelFormatter levelFormatter;

	private LogfmtFormatter(LevelFormatter levelFormatter) {
		this.levelFormatter = levelFormatter;
	}

	/**
	 * Formats events as logfmt, one line of key=value pairs per event like Go's log/slog
	 * text handler, selected with the logfmt encoder scheme.
	 * <p>
	 * Each event is written as one line, for example:
	 * <code>time=2026-10-05T15:04:05.123Z level=INFO logger=com.example.App thread=main msg="hello world" requestId=42</code>
	 * <ul>
	 * <li><code>time</code> is the UTC instant with milliseconds.</li>
	 * <li><code>level</code> is written by the level formatter, by default
	 * <code>TRACE</code>, <code>DEBUG</code>, <code>INFO</code>, <code>WARN</code>, or
	 * <code>ERROR</code>. logfmt does not define level names, and tools differ (Go's slog
	 * is upper case, logrus lower case).</li>
	 * <li>Every key value follows <code>msg</code>, in order.</li>
	 * <li>A throwable adds <code>error</code> (its <code>toString()</code>) and
	 * <code>stacktrace</code>.</li>
	 * </ul>
	 * A value is quoted when it is empty or contains a space, <code>=</code>,
	 * <code>"</code>, or a control character. Inside quotes <code>"</code>,
	 * <code>\</code>, newline, carriage return, and tab are backslash escaped and other
	 * control characters are written as <code>\</code><code>uXXXX</code>, so every event
	 * stays on one line. Characters in keys that logfmt does not allow are replaced with
	 * <code>_</code>.
	 * @param name encoder name, used for property lookup.
	 * @param levelFormatter level formatter; as a property, a level formatter constant
	 * name.
	 * @return formatter.
	 */
	@LogConfigurable(name = "LogfmtFormatterBuilder", prefix = LogProperties.ENCODER_PREFIX)
	static LogFormatter of(@LogConfigurable.KeyParameter String name,
			@LogConfigurable.DefaultParameter("DEFAULT_LEVEL_FORMATTER") @LogConfigurable.ConvertParameter("convertLevelFormatter") LevelFormatter levelFormatter) {
		return new LogfmtFormatter(levelFormatter);
	}

	static LevelFormatter convertLevelFormatter(String value) {
		return LogProperty.enumValue(DefaultLevelFormatter.class, value);
	}

	/*
	 * All key values, see LogFormatter.Builder#logfmtKeyValues().
	 */
	static LogFormatter.EventFormatter keyValues() {
		return LogfmtKeyValuesFormatter.INSTANCE;
	}

	/*
	 * Selected keys, see LogFormatter.Builder#logfmtKeyValues(List,
	 * KeyValueNullStrategy).
	 */
	static LogFormatter.EventFormatter keyValues(List<String> keys, KeyValueNullStrategy nullStrategy) {
		return new SelectedLogfmtKeyValuesFormatter(keys.toArray(new String[0]), nullStrategy);
	}

	@Override
	public void format(StringBuilder output, LogEvent event) {
		output.append("time=");
		TIME.formatTimestamp(output, event.timestamp());
		output.append(" level=");
		levelFormatter.formatLevel(output, event.level());
		output.append(" logger=");
		appendValue(output, event.loggerName());
		output.append(" thread=");
		appendValue(output, event.threadName());
		output.append(" msg=");
		int start = output.length();
		event.formattedMessage(output);
		quoteInPlace(output, start);
		var keyValues = event.keyValues();
		if (!keyValues.isEmpty()) {
			output.append(' ');
			keyValues.forEach(LogfmtKeyValuesFormatter.INSTANCE, 0, output);
		}
		var throwable = event.throwableOrNull();
		if (throwable != null) {
			output.append(" error=");
			appendValue(output, throwable.toString());
			output.append(" stacktrace=");
			start = output.length();
			ThrowableFormatter.appendThrowable(output, throwable);
			trimTrailingLineBreaks(output, start);
			quoteInPlace(output, start);
		}
		output.append('\n');
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

	static void appendValue(StringBuilder output, @Nullable String value) {
		if (value == null) {
			return;
		}
		int start = output.length();
		output.append(value);
		quoteInPlace(output, start);
	}

	/*
	 * Quotes and escapes the text from start to the end of the output if logfmt needs it.
	 * Works in place so the common unquoted case copies nothing, and the quoted case only
	 * shifts the text once for the opening quote; escapes are rare.
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

	private static void trimTrailingLineBreaks(StringBuilder sb, int start) {
		int end = sb.length();
		while (end > start && (sb.charAt(end - 1) == '\n' || sb.charAt(end - 1) == '\r')) {
			end--;
		}
		sb.setLength(end);
	}

}

/*
 * The consumer is a constant and the output is passed as the forEach storage, so
 * iterating key values allocates nothing per event.
 */
enum LogfmtKeyValuesFormatter implements LogFormatter.EventFormatter, KeyValuesConsumer<StringBuilder> {

	INSTANCE;

	@Override
	public void format(StringBuilder output, LogEvent event) {
		event.keyValues().forEach(this, 0, output);
	}

	@Override
	public int accept(KeyValues values, String key, @Nullable String value, int index, StringBuilder storage) {
		if (index > 0) {
			storage.append(' ');
		}
		LogfmtFormatter.appendKey(storage, key);
		storage.append('=');
		LogfmtFormatter.appendValue(storage, value);
		return index + 1;
	}

}

@SuppressWarnings("ArrayRecordComponent")
record SelectedLogfmtKeyValuesFormatter(String[] keys,
		KeyValueNullStrategy nullStrategy) implements LogFormatter.EventFormatter {

	@Override
	public void format(StringBuilder output, LogEvent event) {
		var keyValues = event.keyValues();
		boolean first = true;
		for (String key : keys) {
			@Nullable String value = keyValues.getValueOrNull(key);
			if (value == null) {
				switch (nullStrategy) {
					case SKIP -> {
						continue;
					}
					case EMPTY -> value = "";
					case KEEP -> {
						/* leave null: written as key= */
					}
				}
			}
			if (first) {
				first = false;
			}
			else {
				output.append(' ');
			}
			LogfmtFormatter.appendKey(output, key);
			output.append('=');
			LogfmtFormatter.appendValue(output, value);
		}
	}

}

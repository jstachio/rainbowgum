package io.jstach.rainbowgum.format;

import io.jstach.rainbowgum.LogEvent;
import io.jstach.rainbowgum.LogFormatter;
import io.jstach.rainbowgum.LogFormatter.LevelFormatter;
import io.jstach.rainbowgum.LogFormatter.ThrowableFormatter;
import io.jstach.rainbowgum.LogFormatter.TimestampFormatter;
import io.jstach.rainbowgum.LogProperties;
import io.jstach.rainbowgum.LogProperty;
import io.jstach.rainbowgum.annotation.LogConfigurable;

/*
 * logfmt (https://brandur.org/logfmt). Package private: the public API is the generated
 * LogfmtFormatterBuilder and the logfmt encoder scheme; logfmt key values on their own
 * come from KeyValuesFormatterBuilder. Quoting and escaping are shared with
 * LogfmtKeyValuesFormatter.
 */
final class LogfmtFormatter implements LogFormatter.EventFormatter {

	static final LevelFormatter DEFAULT_LEVEL_FORMATTER = LevelFormatter.of();

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
		return LogProperty.enumValue(LevelFormatterChoice.class, value).levelFormatter();
	}

	/*
	 * The level formatter property's values, named after core's level formatter constants
	 * so the property values stayed the same when this moved out of core.
	 */
	enum LevelFormatterChoice {

		LEVEL_FORMATTER, RIGHT_PAD_LEVEL_FORMATTER;

		LevelFormatter levelFormatter() {
			return switch (this) {
				case LEVEL_FORMATTER -> LevelFormatter.of();
				case RIGHT_PAD_LEVEL_FORMATTER -> LevelFormatter.ofRightPadded();
			};
		}

	}

	@Override
	public void format(StringBuilder output, LogEvent event) {
		output.append("time=");
		TIME.formatTimestamp(output, event.timestamp());
		output.append(" level=");
		levelFormatter.formatLevel(output, event.level());
		output.append(" logger=");
		LogfmtKeyValuesFormatter.appendValue(output, event.loggerName());
		output.append(" thread=");
		LogfmtKeyValuesFormatter.appendValue(output, event.threadName());
		output.append(" msg=");
		int start = output.length();
		event.formattedMessage(output);
		LogfmtKeyValuesFormatter.quoteInPlace(output, start);
		var keyValues = event.keyValues();
		if (!keyValues.isEmpty()) {
			output.append(' ');
			keyValues.forEach(LogfmtKeyValuesFormatter.ALL, 0, output);
		}
		var throwable = event.throwableOrNull();
		if (throwable != null) {
			output.append(" error=");
			LogfmtKeyValuesFormatter.appendValue(output, throwable.toString());
			output.append(" stacktrace=");
			start = output.length();
			ThrowableFormatter.appendThrowable(output, throwable);
			trimTrailingLineBreaks(output, start);
			LogfmtKeyValuesFormatter.quoteInPlace(output, start);
		}
		output.append('\n');
	}

	private static void trimTrailingLineBreaks(StringBuilder sb, int start) {
		int end = sb.length();
		while (end > start && (sb.charAt(end - 1) == '\n' || sb.charAt(end - 1) == '\r')) {
			end--;
		}
		sb.setLength(end);
	}

}

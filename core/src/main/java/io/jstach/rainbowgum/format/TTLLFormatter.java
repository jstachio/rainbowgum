package io.jstach.rainbowgum.format;

import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;

import org.jspecify.annotations.Nullable;

import io.jstach.rainbowgum.LogEvent;
import io.jstach.rainbowgum.LogFormatter;
import io.jstach.rainbowgum.LogFormatter.TimestampFormatter;
import io.jstach.rainbowgum.LogProperties;
import io.jstach.rainbowgum.LogProperty;
import io.jstach.rainbowgum.annotation.LogConfigurable;

/*
 * The TTLL format assembled from its parts. Package private: the public API is the
 * generated TTLLFormatterBuilder, the ttll encoder scheme, and the TTLL choice enums.
 */
final class TTLLFormatter implements TTLL, LogFormatter.EventFormatter {

	static final LogFormatter DEFAULT_TIMESTAMP = TTLL.TimestampFormat.TTLL.formatter();

	static final LogFormatter DEFAULT_THREAD = TTLL.ThreadFormat.NAME.formatter();

	static final LogFormatter DEFAULT_LEVEL = TTLL.LevelFormat.PADDED.formatter();

	static final LogFormatter DEFAULT_LOGGER = TTLL.LoggerFormat.FULL.formatter();

	static final LogFormatter DEFAULT_KEY_VALUES = TTLL.KeyValuesFormat.NONE.formatter();

	private final LogFormatter formatter;

	private TTLLFormatter(LogFormatter formatter) {
		this.formatter = formatter;
	}

	/**
	 * The TTLL (Time, Thread, Level, Logger) text format, the default encoder, with each
	 * part configurable, see TTLL.
	 * <p>
	 * The defaults produce <code>12:00:00.123 [main] INFO  com.example.App - hello</code>
	 * followed by any stack trace. Parts set to none are left out along with their
	 * separating space. As properties of the encoder, each part is one of the lowercase
	 * names of its {@link TTLL} enum, for example
	 * <code>logging.encoder.console.keyValues=logfmt</code>. The timestamp property also
	 * accepts a DateTimeFormatter pattern in UTC. The color property is a TTLL.ColorTheme
	 * name, with <code>true</code> and <code>false</code> as aliases; when not set the
	 * rainbowgum theme is used if the console supports ANSI.
	 * @param name encoder name, used for property lookup.
	 * @param timestamp time formatter, see TTLL.TimestampFormat.
	 * @param thread thread formatter in square brackets, see TTLL.ThreadFormat.
	 * @param level level formatter, see TTLL.LevelFormat.
	 * @param logger logger name formatter, see TTLL.LoggerFormat.
	 * @param keyValues key values formatter in braces, see TTLL.KeyValuesFormat.
	 * @param color ANSI color theme, see TTLL.ColorTheme.
	 * @return formatter.
	 */
	@LogConfigurable(name = "TTLLFormatterBuilder", prefix = LogProperties.ENCODER_PREFIX)
	static LogFormatter of(@LogConfigurable.KeyParameter String name,
			@LogConfigurable.DefaultParameter("DEFAULT_TIMESTAMP") @LogConfigurable.ConvertParameter("convertTimestamp") LogFormatter timestamp,
			@LogConfigurable.DefaultParameter("DEFAULT_THREAD") @LogConfigurable.ConvertParameter("convertThread") LogFormatter thread,
			@LogConfigurable.DefaultParameter("DEFAULT_LEVEL") @LogConfigurable.ConvertParameter("convertLevel") LogFormatter level,
			@LogConfigurable.DefaultParameter("DEFAULT_LOGGER") @LogConfigurable.ConvertParameter("convertLogger") LogFormatter logger,
			@LogConfigurable.DefaultParameter("DEFAULT_KEY_VALUES") @LogConfigurable.ConvertParameter("convertKeyValues") LogFormatter keyValues,
			@LogConfigurable.ConvertParameter("convertColor") TTLL.@Nullable ColorTheme color) {
		boolean ansi = color == null ? AnsiSupport.isAnsiSupported() : color == TTLL.ColorTheme.RAINBOWGUM;
		var b = LogFormatter.builder();
		boolean empty = true;
		empty = part(b, ansi ? Ansi.colored(Ansi.CYAN, timestamp) : timestamp, empty);
		if (!thread.isNoop()) {
			var bracketed = LogFormatter.builder().text("[").add(thread).text("]").build();
			empty = part(b, ansi ? Ansi.colored(Ansi.FAINT, bracketed) : bracketed, empty);
		}
		empty = part(b, ansi && !level.isNoop() ? new LevelHighlightFormatter(level) : level, empty);
		empty = part(b, ansi ? Ansi.colored(Ansi.MAGENTA, logger) : logger, empty);
		if (!keyValues.isNoop()) {
			b.add(ansi ? new BracedKeyValuesFormatter(keyValues, " " + Ansi.start(Ansi.FAINT) + "{", "}" + Ansi.RESET)
					: new BracedKeyValuesFormatter(keyValues));
		}
		if (!empty) {
			b.text(" - ");
		}
		b.message();
		b.newline();
		b.throwable();
		return new TTLLFormatter(b.build());
	}

	private static boolean part(LogFormatter.Builder b, LogFormatter part, boolean empty) {
		if (part.isNoop()) {
			return empty;
		}
		if (!empty) {
			b.text(" ");
		}
		b.add(part);
		return false;
	}

	static LogFormatter convertTimestamp(String value) {
		for (var choice : TTLL.TimestampFormat.values()) {
			if (choice.name().equalsIgnoreCase(value)) {
				return choice.formatter();
			}
		}
		try {
			return TimestampFormatter.of(DateTimeFormatter.ofPattern(value).withZone(ZoneOffset.UTC));
		}
		catch (IllegalArgumentException e) {
			throw new IllegalArgumentException("'" + value
					+ "' is neither a timestamp format (ttll, iso, none) nor a valid DateTimeFormatter pattern: "
					+ e.getMessage(), e);
		}
	}

	static LogFormatter convertThread(String value) {
		return LogProperty.enumValue(TTLL.ThreadFormat.class, value).formatter();
	}

	static LogFormatter convertLevel(String value) {
		return LogProperty.enumValue(TTLL.LevelFormat.class, value).formatter();
	}

	static LogFormatter convertLogger(String value) {
		return LogProperty.enumValue(TTLL.LoggerFormat.class, value).formatter();
	}

	static TTLL.ColorTheme convertColor(String value) {
		return TTLL.ColorTheme.parse(value);
	}

	static LogFormatter convertKeyValues(String value) {
		return LogProperty.enumValue(TTLL.KeyValuesFormat.class, value).formatter();
	}

	@Override
	public void format(StringBuilder output, LogEvent event) {
		formatter.format(output, event);
	}

}

/*
 * The last dot separated component of the logger name.
 */
enum ShortLoggerNameFormatter implements LogFormatter.EventFormatter {

	INSTANCE;

	@Override
	public void format(StringBuilder output, LogEvent event) {
		String name = event.loggerName();
		output.append(name, name.lastIndexOf('.') + 1, name.length());
	}

}

/*
 * Writes " {" key values "}" but leaves the braces out entirely when the key values
 * formatter writes nothing, for example an event without key values.
 */
record BracedKeyValuesFormatter(LogFormatter keyValuesFormatter, String open,
		String close) implements LogFormatter.EventFormatter {

	BracedKeyValuesFormatter(LogFormatter keyValuesFormatter) {
		this(keyValuesFormatter, " {", "}");
	}

	@Override
	public void format(StringBuilder output, LogEvent event) {
		int start = output.length();
		output.append(open);
		keyValuesFormatter.format(output, event);
		if (output.length() == start + open.length()) {
			output.setLength(start);
		}
		else {
			output.append(close);
		}
	}

}

/*
 * ANSI escape codes, the same sequences the pattern encoder writes.
 */
final class Ansi {

	static final String CYAN = "36";

	static final String MAGENTA = "35";

	static final String FAINT = "2;39";

	static final String BOLD_RED = "1;31";

	static final String RED = "31";

	static final String BOLD_BLUE = "1;34";

	static final String DEFAULT = "39";

	static final String RESET = "\033[0;39m";

	private Ansi() {
	}

	static String start(String code) {
		return "\033[" + code + "m";
	}

	static LogFormatter colored(String code, LogFormatter formatter) {
		if (formatter.isNoop()) {
			return formatter;
		}
		return LogFormatter.builder().text(start(code)).add(formatter).text(RESET).build();
	}

}

/*
 * Colors the level by severity like the pattern encoder's default highlight: error bold
 * red, warn red, info bold blue, others the default color.
 */
record LevelHighlightFormatter(LogFormatter level) implements LogFormatter.EventFormatter {

	@Override
	public void format(StringBuilder output, LogEvent event) {
		String code = switch (event.level()) {
			case ERROR -> Ansi.BOLD_RED;
			case WARNING -> Ansi.RED;
			case INFO -> Ansi.BOLD_BLUE;
			default -> Ansi.DEFAULT;
		};
		output.append(Ansi.start(code));
		level.format(output, event);
		output.append(Ansi.RESET);
	}

}

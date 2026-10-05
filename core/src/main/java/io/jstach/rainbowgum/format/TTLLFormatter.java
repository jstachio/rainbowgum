package io.jstach.rainbowgum.format;

import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;

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
	 * <code>logging.encoder.console.keyValues=logfmt</code>.
	 * @param name encoder name, used for property lookup.
	 * @param timestamp time formatter; as a property a TTLL.TimestampFormat name or a
	 * DateTimeFormatter pattern in UTC.
	 * @param thread thread formatter, written in square brackets; as a property a
	 * TTLL.ThreadFormat name.
	 * @param level level formatter; as a property a TTLL.LevelFormat name.
	 * @param logger logger name formatter; as a property a TTLL.LoggerFormat name.
	 * @param keyValues key values formatter, written in braces; as a property a
	 * TTLL.KeyValuesFormat name.
	 * @return formatter.
	 */
	@LogConfigurable(name = "TTLLFormatterBuilder", prefix = LogProperties.ENCODER_PREFIX)
	static LogFormatter of(@LogConfigurable.KeyParameter String name,
			@LogConfigurable.DefaultParameter("DEFAULT_TIMESTAMP") @LogConfigurable.ConvertParameter("convertTimestamp") LogFormatter timestamp,
			@LogConfigurable.DefaultParameter("DEFAULT_THREAD") @LogConfigurable.ConvertParameter("convertThread") LogFormatter thread,
			@LogConfigurable.DefaultParameter("DEFAULT_LEVEL") @LogConfigurable.ConvertParameter("convertLevel") LogFormatter level,
			@LogConfigurable.DefaultParameter("DEFAULT_LOGGER") @LogConfigurable.ConvertParameter("convertLogger") LogFormatter logger,
			@LogConfigurable.DefaultParameter("DEFAULT_KEY_VALUES") @LogConfigurable.ConvertParameter("convertKeyValues") LogFormatter keyValues) {
		var b = LogFormatter.builder();
		boolean empty = true;
		empty = part(b, timestamp, empty);
		if (!thread.isNoop()) {
			empty = part(b, LogFormatter.builder().text("[").add(thread).text("]").build(), empty);
		}
		empty = part(b, level, empty);
		empty = part(b, logger, empty);
		if (!keyValues.isNoop()) {
			b.add(new BracedKeyValuesFormatter(keyValues));
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
record BracedKeyValuesFormatter(LogFormatter keyValuesFormatter) implements LogFormatter.EventFormatter {

	@Override
	public void format(StringBuilder output, LogEvent event) {
		int start = output.length();
		output.append(" {");
		keyValuesFormatter.format(output, event);
		if (output.length() == start + 2) {
			output.setLength(start);
		}
		else {
			output.append('}');
		}
	}

}

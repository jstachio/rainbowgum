package io.jstach.rainbowgum;

import java.io.PrintWriter;
import java.lang.System.Logger.Level;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.eclipse.jdt.annotation.Nullable;

import io.jstach.rainbowgum.KeyValues.KeyValuesConsumer;
import io.jstach.rainbowgum.LogFormatter.EventFormatter;
import io.jstach.rainbowgum.LogFormatter.LevelFormatter;
import io.jstach.rainbowgum.LogFormatter.ThrowableFormatter;
import io.jstach.rainbowgum.LogFormatter.TimestampFormatter;

/**
 * Formats a log event using a {@link StringBuilder}. <strong>All formatters should be
 * thread-safe!</strong>.
 * <p>
 * The appender will make sure the {@link StringBuilder} is not shared with multiple
 * threads so the formatter does not have to synchronize/lock on and should definitely not
 * do that.
 * <p>
 * Because of various invariants the preferred way to compose formatters is to use the
 * {@linkplain #builder() builder} which will do some optimization like combining static
 * formatters etc.
 *
 * @see #builder()
 * @see LogFormatter.EventFormatter
 * @see LogEncoder#of(LogFormatter)
 * @apiNote This class is sealed. An interface that has the same contract that can be
 * implemented is {@link EventFormatter}.
 */
public sealed interface LogFormatter {

	/**
	 * Formats a log event.
	 * @param output buffer.
	 * @param event log event.
	 * @see EventFormatter
	 */
	public void format(StringBuilder output, LogEvent event);

	/**
	 * See {@link EventFormatter#builder()}.
	 * @return builder.
	 */
	public static Builder builder() {
		return new Builder();
	}

	/**
	 * Create a formatter using event formatter that is a lambda so that
	 * {@code LogFormater.of((o, e) -> ...); } works.
	 * @param e will be returned.
	 * @return passed in formatter.
	 * @apiNote this is for ergonomics because LogFormatter is sealed.
	 */
	public static EventFormatter of(EventFormatter e) {
		return e;
	}

	/**
	 * Ask the formatter if will do anything.
	 * @return true if promises not to write to builder.
	 * @see #noop()
	 */
	default boolean isNoop() {
		return NoopFormatter.INSTANCE == this;
	}

	/**
	 * A special formatter that will do nothing. It is a singleton so identity comparison
	 * can be used.
	 * @return a formatter that implements all formatting interfaces but does nothing.
	 */
	public static NoopFormatter noop() {
		return NoopFormatter.INSTANCE;
	}

	/**
	 * Creates a static formatter of text.
	 * @param text immutable text.
	 * @return immutable static formatter.
	 */
	public static StaticFormatter of(String text) {
		return new StaticFormatter(text);
	}

	/**
	 * A special formatter that will append static text.
	 *
	 * @param content static text.
	 */
	public record StaticFormatter(String content) implements LogFormatter {
		@Override
		public void format(StringBuilder output, LogEvent event) {
			output.append(content);
		}

		/**
		 * Creates a new formatter by concat the {@link #content()}. This is mainly used
		 * by the {@link EventFormatter#builder()} to coalesce multiple static text.
		 * @param next the text that will follow this formatter.
		 * @return new formatter.
		 */
		public StaticFormatter concat(StaticFormatter next) {
			return new StaticFormatter(this.content + next.content);
		}

		/**
		 * Coalesce formatters that can be such as {@link StaticFormatter}.
		 * @param formatters list of formatters in the order of which they will be
		 * executed.
		 * @return an array of formatters where static formatters next to each other will
		 * be coalesced.
		 */
		private static LogFormatter[] coalesce(List<? extends LogFormatter> formatters) {
			var flattened = CompositeFormatter.flatten(formatters);
			List<LogFormatter> resolved = new ArrayList<>();
			StaticFormatter current = null;
			for (var f : flattened) {
				if (f.isNoop()) {
					continue;
				}
				else if (current == null && f instanceof StaticFormatter sf) {
					current = sf;
				}
				else if (current != null && f instanceof StaticFormatter sf) {
					current = current.concat(sf);
				}
				else if (current != null) {
					resolved.add(current);
					resolved.add(f);
					current = null;
				}
				else {
					resolved.add(f);
				}
			}
			if (current != null) {
				resolved.add(current);
			}
			return resolved.toArray(new LogFormatter[] {});
		}

		@Override
		public String toString() {
			return "STATIC['" + content + "']";
		}
	}

	/**
	 * Log formatter builder that is composed of other formatters. The
	 * {@link #add(LogFormatter)} are executed in insertion order. <strong> This builder
	 * is smart and will coalesce and consolidate formatters! </strong> For example if
	 * only formatter is added to the builder it will be returned instead of a new
	 * formatter.
	 */
	public final static class Builder {

		private List<LogFormatter> formatters = new ArrayList<>();

		private Builder() {
		}

		/**
		 * Adds a formatter.
		 * @param formatter formatter to be added the list of formatters.
		 * @return this builder.
		 */
		public Builder add(LogFormatter formatter) {
			formatters.add(formatter);
			return this;
		}

		/**
		 * Adds an event formatter.
		 * @param formatter event formatter to be added the list of formatters.
		 * @return this builder.
		 */
		public Builder event(LogFormatter.EventFormatter formatter) {
			formatters.add(formatter);
			return this;
		}

		/**
		 * Append the timestamp in ISO format.
		 * @return this builder.
		 */
		public Builder timeStamp() {
			formatters.add(DefaultInstantFormatter.ISO);
			return this;
		}

		/**
		 * Formatter for {@link LogEvent#timestamp()} derived from standard
		 * {@link DateTimeFormatter}.
		 * @param dateTimeFormatter formatter for {@link LogEvent#timestamp()}
		 * @return this builder.
		 */
		public Builder timeStamp(DateTimeFormatter dateTimeFormatter) {
			formatters.add(new DateTimeFormatterInstantFormatter(dateTimeFormatter));
			return this;
		}

		/**
		 * Adds the default level formatter.
		 * @return this builder.
		 * @see LevelFormatter
		 */
		public Builder level() {
			formatters.add(LogFormatter.LevelFormatter.of());
			return this;
		}

		/**
		 * Adds the default logger name formatter.
		 * @return this builder.
		 */
		public Builder loggerName() {
			formatters.add(DefaultNameFormatter.LOGGER_NAME_FORMATTER);
			return this;
		}

		/**
		 * Formats the message by calling
		 * {@link LogEvent#formattedMessage(StringBuilder)}.
		 * @return this builder.
		 */
		public Builder message() {
			formatters.add(DefaultMessageFormatter.MESSAGE_FORMATTER);
			return this;
		}

		/**
		 * Appends static text.
		 * @param content static text.
		 * @return this builder.
		 * @see StaticFormatter
		 */
		public Builder text(String content) {
			formatters.add(new StaticFormatter(content));
			return this;
		}

		/**
		 * Creates a formatter that will print <strong>ALL</strong> of the key values by
		 * percent encoding (RFC 3986 URI aka the format usually used in
		 * {@link URI#getQuery()}).
		 * @return formatter.
		 */
		public Builder keyValues() {
			return add(DefaultKeyValuesFormatter.INSTANCE);
		}

		/**
		 * Creates a formatter that will print the key values in order of the passed in
		 * keys if they exist in percent encoding (RFC 3986 URI aka the format usually
		 * used in {@link URI#getQuery()}).
		 * @param keys keys where order is important.
		 * @return this.
		 */
		public Builder keyValues(List<String> keys) {
			if (keys.isEmpty()) {
				return this;
			}
			return add(new ListKeyValuesFormatter(keys));
		}

		/**
		 * Creates a formatter that will print a single key value in percent encoding (RFC
		 * 3986 URI aka the format usually used in {@link URI#getQuery()}).
		 * @param key key to select.
		 * @param fallback if the value is null the fallback will be used.
		 * @return this.
		 */
		public Builder keyValues(String key, @Nullable String fallback) {
			return add(new SingleKeyValueFormatter(key, fallback));
		}

		/**
		 * Appends a space.
		 * @return this builder.
		 */
		public Builder space() {
			formatters.add(new StaticFormatter(" "));
			return this;
		}

		/**
		 * Appends a newline using the platforms line separator.
		 * @return this builder.
		 */
		public Builder newline() {
			text(System.lineSeparator());
			return this;
		}

		/**
		 * Appends a thread name : {@link Thread#getName()}.
		 * @return this builder.
		 */
		public Builder threadName() {
			formatters.add(DefaultThreadFormatter.THREAD_NAME_FORMATTER);
			return this;
		}

		/**
		 * Appends a thread ID : {@link Thread#threadId()}.
		 * @return this builder.
		 */
		public Builder threadId() {
			formatters.add(DefaultThreadFormatter.THREAD_ID_FORMATTER);
			return this;
		}

		/**
		 * Appends the events throwable stack trace.
		 * @return this.
		 */
		public Builder throwable() {
			formatters.add(ThrowableFormatter.of());
			return this;
		}

		/**
		 * Will create a generic log formatter that has the inner formatters coalesced if
		 * possible and will noop if there are no formatters.
		 * @return flattened formatter.
		 */
		public LogFormatter build() {
			var array = StaticFormatter.coalesce(formatters);
			if (array.length == 0) {
				return NoopFormatter.INSTANCE;
			}
			if (array.length == 1) {
				return array[0];
			}
			return EventFormatter.of(formatters);
		}

	}

	/**
	 * Generic event formatting that is lambda friendly.
	 */
	@FunctionalInterface
	public non-sealed interface EventFormatter extends LogFormatter {

		@Override
		public void format(StringBuilder output, LogEvent event);

		private static EventFormatter of(List<? extends LogFormatter> formatters) {
			return new CompositeFormatter(StaticFormatter.coalesce(formatters));
		}

	}

	/**
	 * Formats a {@link Level}.
	 */
	public non-sealed interface LevelFormatter extends LogFormatter {

		/**
		 * Formats the level.
		 * @param output buffer.
		 * @param level level.
		 */
		void formatLevel(StringBuilder output, Level level);

		@Override
		default void format(StringBuilder output, LogEvent event) {
			formatLevel(output, event.level());
		}

		/**
		 * Default implementation calls {@link LevelFormatter#toString(Level)}
		 * @return formatter.
		 */
		public static LevelFormatter of() {
			return DefaultLevelFormatter.LEVEL_FORMATTER;
		}

		/**
		 * Default implementation calls {@link LevelFormatter#rightPadded(Level)}
		 * @return formatter.
		 */
		public static LevelFormatter ofRightPadded() {
			return DefaultLevelFormatter.RIGHT_PAD_LEVEL_FORMATTER;
		}

		/**
		 * Turns a Level into a SLF4J like level String that is all upper case.
		 * {@link Level#ALL} is "<code>ERROR</code>", {@link Level#OFF} is
		 * "<code>TRACE</code>" and {@link Level#WARNING} is "<code>WARN</code>".
		 * @param level system logger level.
		 * @return upper case string of level.
		 */
		public static String toString(Level level) {
			return switch (level) {
				case DEBUG -> "DEBUG";
				case ALL -> "ERROR";
				case ERROR -> "ERROR";
				case INFO -> "INFO";
				case OFF -> "TRACE";
				case TRACE -> "TRACE";
				case WARNING -> "WARN";
			};
		}

		/**
		 * Turns a Level into a SLF4J like level String that is all upper case and same
		 * length with right padding. {@link Level#ALL} is "<code>ERROR</code>",
		 * {@link Level#OFF} is "<code>TRACE</code>" and {@link Level#WARNING} is
		 * "<code>WARN</code>".
		 * @param level system logger level.
		 * @return upper case string of level.
		 */
		public static String rightPadded(Level level) {
			return switch (level) {
				case DEBUG -> /*   */ "DEBUG";
				case ALL -> /*     */ "ERROR";
				case ERROR -> /*   */ "ERROR";
				case INFO -> /*    */ "INFO ";
				case OFF -> /*     */ "TRACE";
				case TRACE -> /*   */ "TRACE";
				case WARNING -> /* */ "WARN ";
			};
		}

	}

	/**
	 * Formats event timestamps.
	 */
	public non-sealed interface TimestampFormatter extends LogFormatter {

		/**
		 * The default timestamp format used in many logging frameworks which does not
		 * have dates and only time at millisecond precision.
		 * <p>
		 * It is called TTLL as that is the name of the format where it is used in
		 * logback, log4j etc.
		 */
		public static String TTLL_TIME_FORMAT = "HH:mm:ss.SSS";

		/**
		 * Format timestamp.
		 * @param output buffer.
		 * @param instant timestamp.
		 */
		void formatTimestamp(StringBuilder output, Instant instant);

		@Override
		default void format(StringBuilder output, LogEvent event) {
			formatTimestamp(output, event.timestamp());
		}

		/**
		 * Formats timestamp using {@link #TTLL_TIME_FORMAT}.
		 * @return formatter.
		 */
		public static TimestampFormatter of() {
			return DefaultInstantFormatter.TTLL;
		}

		/**
		 * Formats a timestamp using ISO format.
		 * @return formatter.
		 */
		public static TimestampFormatter ofISO() {
			return DefaultInstantFormatter.ISO;
		}

		/**
		 * Micro seconds over the events last second. This is for logback compatibility.
		 * @return microseconds zero padded.
		 */
		public static TimestampFormatter ofMicros() {
			return DefaultInstantFormatter.MICROS;
		}

		/**
		 * Formats a timestamp using standard JDK date time formatter.
		 * @param dateTimeFormatter date time formatter.
		 * @return timestamp formatter.
		 */
		public static TimestampFormatter of(DateTimeFormatter dateTimeFormatter) {
			return new DateTimeFormatterInstantFormatter(dateTimeFormatter);
		}

	}

	/**
	 * Formats a throwable.
	 */
	public non-sealed interface ThrowableFormatter extends LogFormatter {

		/**
		 * Formats a throwable and appends.
		 * @param output buffer.
		 * @param throwable throwable.
		 */
		void formatThrowable(StringBuilder output, Throwable throwable);

		@Override
		default void format(StringBuilder output, LogEvent event) {
			var t = event.throwableOrNull();
			if (t != null) {
				formatThrowable(output, t);
			}
		}

		/**
		 * Default implementation uses {@link Throwable#printStackTrace(PrintWriter)}.
		 * @return formatter.
		 */
		public static ThrowableFormatter of() {
			return DefaultThrowableFormatter.INSTANT;
		}

		/**
		 * Convenience to append a throwable to string builder.
		 * @param b buffer.
		 * @param t throwable.
		 * @apiNote this call creates garbage.
		 */
		public static void appendThrowable(StringBuilder b, Throwable t) {
			/*
			 * TODO optimize
			 */
			t.printStackTrace(Internal.StringBuilderPrintWriter.of(b));
		}

	}

	/**
	 * Tests if the log formatter is noop or is null which will be considered as noop.
	 * @param logFormatter formatter which <strong>can be <code>null</code></strong>!
	 * @return true if the formatter should not be used.
	 */
	public static boolean isNoopOrNull(@Nullable LogFormatter logFormatter) {
		return logFormatter == null || logFormatter.isNoop();
	}

	/**
	 * Pads the right hand side of text with space.
	 * @param sb buffer.
	 * @param s string that will be appended first (left hand) and will not be longer than
	 * the <code>n</code> parameter.
	 * @param n the size of string. If the size is bigger than passed in string the result
	 * will have padding otherwise the passed in string will be cut to the size of this
	 * parameter.
	 */
	public static void padRight(StringBuilder sb, CharSequence s, int n) {
		int length = s.length();
		if (length >= n) {
			sb.append(s, 0, n);
			return;
		}
		sb.append(s);
		spacePad(sb, n - length);
	}

	/**
	 * Pads the left hand side of text with space.
	 * @param sb buffer.
	 * @param s string that will be appended second (right hand) and will not be longer
	 * than the <code>n</code> parameter.
	 * @param n the size of string. If the size is bigger than passed in string the result
	 * will have padding otherwise the passed in string will be cut to the size of this
	 * parameter.
	 */
	public static void padLeft(StringBuilder sb, CharSequence s, int n) {
		int length = s.length();
		if (length >= n) {
			sb.append(s, 0, n);
			return;
		}
		spacePad(sb, n - length);
		sb.append(s);
	}

	/**
	 * Fast space padding method.
	 */
	private static void spacePad(final StringBuilder sbuf, final int length) {
		int l = length;
		while (l >= 32) {
			sbuf.append(CompositeFormatter.SPACES[5]);
			l -= 32;
		}

		for (int i = 4; i >= 0; i--) {
			if ((l & (1 << i)) != 0) {
				sbuf.append(CompositeFormatter.SPACES[i]);
			}
		}
	}

	/**
	 * A special formatter that will do nothing.
	 */
	enum NoopFormatter implements TimestampFormatter, ThrowableFormatter, LevelFormatter {

		/**
		 * instance.
		 */
		INSTANCE;

		@Override
		public void formatThrowable(StringBuilder output, Throwable throwable) {
		}

		@Override
		public void formatTimestamp(StringBuilder output, Instant instant) {
		}

		@Override
		public void formatLevel(StringBuilder output, Level level) {
		}

		@Override
		public void format(StringBuilder output, LogEvent event) {
		}

	}

}

record CompositeFormatter(LogFormatter[] formatters) implements EventFormatter {

	static String[] SPACES = { " ", "  ", "    ", "        ", // 1,2,4,8 spaces
			"                ", // 16 spaces
			"                                " }; // 32 spaces

	@Override
	public void format(StringBuilder output, LogEvent event) {
		for (var formatter : formatters) {
			formatter.format(output, event);
		}
	}

	@Override
	public String toString() {
		return getClass().getSimpleName() + Arrays.toString(formatters);
	}

	public static List<LogFormatter> flatten(List<? extends LogFormatter> formatters) {
		return List.copyOf(_flatten(formatters));
	}

	private static List<LogFormatter> _flatten(List<? extends LogFormatter> formatters) {
		List<LogFormatter> result = new ArrayList<>();
		for (var f : formatters) {
			if (f instanceof CompositeFormatter cf) {
				result.addAll(_flatten(cf));
			}
			else {
				result.add(f);
			}
		}
		return result;
	}

	private static List<LogFormatter> _flatten(CompositeFormatter formatter) {
		var formatters = formatter.formatters;
		return _flatten(Arrays.asList(formatters));
	}

}

enum DefaultMessageFormatter implements LogFormatter {

	MESSAGE_FORMATTER;

	@Override
	public void format(StringBuilder output, LogEvent event) {
		event.formattedMessage(output);

	}

}

enum DefaultNameFormatter implements LogFormatter {

	LOGGER_NAME_FORMATTER;

	@Override
	public void format(StringBuilder output, LogEvent event) {
		output.append(event.loggerName());

	}

}

enum DefaultLevelFormatter implements LevelFormatter {

	LEVEL_FORMATTER {
		@Override
		public void formatLevel(StringBuilder output, Level level) {
			output.append(LevelFormatter.toString(level));
		}
	},
	RIGHT_PAD_LEVEL_FORMATTER {
		@Override
		public void formatLevel(StringBuilder output, Level level) {
			output.append(LevelFormatter.rightPadded(level));
		}
	}

}

enum DefaultThreadFormatter implements LogFormatter {

	THREAD_NAME_FORMATTER() {
		@Override
		public void format(StringBuilder output, LogEvent event) {
			output.append(event.threadName());
		}
	},
	THREAD_ID_FORMATTER() {
		@Override
		public void format(StringBuilder output, LogEvent event) {
			output.append(event.threadId());
		}
	}

}

enum DefaultInstantFormatter implements TimestampFormatter {

	TTLL(DateTimeFormatter.ofPattern(TTLL_TIME_FORMAT).withZone(ZoneId.from(ZoneOffset.UTC))),
	ISO(DateTimeFormatter.ISO_DATE_TIME.withZone(ZoneId.from(ZoneOffset.UTC))),
	MICROS(DateTimeFormatter.ISO_DATE_TIME) {
		@Override
		@SuppressWarnings("JavaInstantGetSecondsGetNano")
		public void formatTimestamp(StringBuilder output, Instant instant) {
			int nanos = instant.getNano();

			int millis_and_micros = nanos / 1000;
			int micros = millis_and_micros % 1000;

			if (micros >= 100) {
				output.append(micros);
			}
			else if (micros >= 10) {
				output.append("0").append(micros);
			}
			else {
				output.append("00").append(micros);
			}
		}
	};

	private final DateTimeFormatter formatter;

	DefaultInstantFormatter(DateTimeFormatter formatter) {
		this.formatter = formatter;
	}

	@Override
	public void formatTimestamp(StringBuilder output, Instant instant) {
		formatter.formatTo(instant, output);
	}

}

record DateTimeFormatterInstantFormatter(DateTimeFormatter dateTimeFormatter) implements TimestampFormatter {

	@Override
	public void formatTimestamp(StringBuilder output, Instant instant) {
		dateTimeFormatter.formatTo(instant, output);
	}
}

enum DefaultThrowableFormatter implements ThrowableFormatter {

	INSTANT;

	@Override
	public void formatThrowable(StringBuilder output, Throwable throwable) {
		ThrowableFormatter.appendThrowable(output, throwable);
	}

}

enum DefaultKeyValuesFormatter implements LogFormatter, KeyValuesConsumer<StringBuilder> {

	INSTANCE;

	@Override
	public void format(StringBuilder output, LogEvent event) {
		var keyValues = event.keyValues();
		keyValues.forEach(this, 0, output);
	}

	static void formatKeyValue(StringBuilder output, String k, @Nullable String v) {
		PercentCodec.encode(output, k, StandardCharsets.UTF_8);
		if (v != null) {
			output.append("=");
			PercentCodec.encode(output, v, StandardCharsets.UTF_8);
		}
	}

	@Override
	public int accept(KeyValues values, String key, @Nullable String value, int index, StringBuilder storage) {
		if (index > 0) {
			storage.append("&");
		}
		formatKeyValue(storage, key, value);
		return index + 1;
	}

}

final class ListKeyValuesFormatter implements LogFormatter {

	private final String[] keys;

	@SuppressWarnings("nullness")
	ListKeyValuesFormatter(List<String> keys) {
		var ks = List.copyOf(keys);
		this.keys = ks.toArray(new String[] {});
	}

	@Override
	public void format(StringBuilder output, LogEvent event) {
		var kvs = event.keyValues();
		formatKeyValues(output, kvs);
	}

	void formatKeyValues(StringBuilder output, KeyValues keyValues) {
		boolean first = true;
		for (String k : keys) {
			String v = keyValues.getValueOrNull(k);
			if (v == null) {
				continue;
			}
			if (first) {
				first = false;
			}
			else {
				output.append("&");
			}
			DefaultKeyValuesFormatter.formatKeyValue(output, k, v);
		}
	}

}

record SingleKeyValueFormatter(String key, @Nullable String fallback) implements LogFormatter {
	@Override
	public void format(StringBuilder output, LogEvent event) {
		var kvs = event.keyValues();
		formatKeyValues(output, kvs);
	}

	void formatKeyValues(StringBuilder output, KeyValues keyValues) {
		String v = keyValues.getValueOrNull(key);
		if (v == null) {
			v = fallback;
		}
		DefaultKeyValuesFormatter.formatKeyValue(output, key, v);
	}

}

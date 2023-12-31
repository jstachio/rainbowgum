package io.jstach.rainbowgum;

import java.io.IOException;
import java.io.PrintWriter;
import java.io.Writer;
import java.lang.System.Logger.Level;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

import org.eclipse.jdt.annotation.Nullable;

import io.jstach.rainbowgum.KeyValues.KeyValuesConsumer;
import io.jstach.rainbowgum.LogFormatter.EventFormatter;
import io.jstach.rainbowgum.LogFormatter.KeyValuesFormatter;
import io.jstach.rainbowgum.LogFormatter.LevelFormatter;
import io.jstach.rainbowgum.LogFormatter.MessageFormatter;
import io.jstach.rainbowgum.LogFormatter.NameFormatter;
import io.jstach.rainbowgum.LogFormatter.ThreadFormatter;
import io.jstach.rainbowgum.LogFormatter.ThrowableFormatter;
import io.jstach.rainbowgum.LogFormatter.TimestampFormatter;

/**
 * Formats a log event using a {@link StringBuilder}. <strong>All formatters should be
 * threadsafe!</strong>.
 * <p>
 * The appender will make sure the {@link StringBuilder} is not shared with multiple
 * threads so the formatter does not have to synchronize/lock on and should definitely not
 * do that.
 *
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
	public static EventFormatter.Builder builder() {
		return EventFormatter.builder();
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
	 * @return a formatter tha implements all formatting interfaces but does nothing.
	 */
	public static NoopFormatter noop() {
		return NoopFormatter.INSTANCE;
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
		 * by the {@link EventFormatter#builder()} to coallesce multiple static text.
		 * @param next the text that will follow this formatter.
		 * @return new formatter.
		 */
		public StaticFormatter concat(StaticFormatter next) {
			return new StaticFormatter(this.content + next.content);
		}

		/**
		 * Coalese formatters that can be such as {@link StaticFormatter}.
		 * @param formatters list of formatters in the order of which they will be
		 * executed.
		 * @return an array of formatters where static formatters next to each other will
		 * be coalesced.
		 */
		private static LogFormatter[] coalesce(List<? extends LogFormatter> formatters) {
			List<LogFormatter> resolved = new ArrayList<>();
			StaticFormatter current = null;
			for (var f : formatters) {
				if (current == null && f instanceof StaticFormatter sf) {
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
	}

	/**
	 * Generic event formatting that is lambda friendly.
	 */
	@FunctionalInterface
	public non-sealed interface EventFormatter extends LogFormatter {

		public void format(StringBuilder output, LogEvent event);

		private static EventFormatter of(List<? extends LogFormatter> formatters) {
			return new DefaultEventFormatter(StaticFormatter.coalesce(formatters));
		}

		/**
		 * Builder
		 * @return builder.
		 */
		public static Builder builder() {
			return new Builder();
		}

		/**
		 * Log formatter builder that is composed of other formatters. The
		 * {@link #add(LogFormatter)} are executed in insertion order.
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
				formatters.add(new DateTimeFormatterInstantFormatter(DateTimeFormatter.ISO_INSTANT));
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
			 * @see NameFormatter
			 */
			public Builder loggerName() {
				formatters.add(LogFormatter.NameFormatter.of());
				return this;
			}

			/**
			 * Formats the message by calling
			 * {@link LogEvent#formattedMessage(StringBuilder)}.
			 * @return this builder.
			 * @see MessageFormatter
			 */
			public Builder message() {
				formatters.add(LogFormatter.MessageFormatter.of());
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
			 * Appends a space.
			 * @return this builder.
			 */
			public Builder space() {
				formatters.add(new StaticFormatter(" "));
				return this;
			}

			/**
			 * Appends a newline.
			 * @return this builder.
			 */
			public Builder newline() {
				text(Defaults.NEW_LINE);
				return this;
			}

			/**
			 * Appends a thread name.
			 * @return this builder.
			 */
			public Builder threadName() {
				formatters.add(ThreadFormatter.of());
				return this;
			}

			/**
			 * Builds the formatter.
			 * @return this builder.
			 */
			public EventFormatter build() {
				return EventFormatter.of(formatters);
			}

		}

	}

	/**
	 * Formats {@link LogEvent#formattedMessage(StringBuilder)}.
	 */
	public non-sealed interface MessageFormatter extends LogFormatter {

		@Override
		default void format(StringBuilder output, LogEvent event) {
			formatMessage(output, event);
		}

		/**
		 * Formats the message from the log event.
		 * @param output buffer.
		 * @param event log event.
		 */
		public void formatMessage(StringBuilder output, LogEvent event);

		/**
		 * The default implementation.
		 * @return formatter.
		 */
		public static MessageFormatter of() {
			return DefaultMessageFormatter.INSTANT;
		}

	}

	/**
	 * Formats a logger name.
	 */
	public non-sealed interface NameFormatter extends LogFormatter {

		@Override
		default void format(StringBuilder output, LogEvent event) {
			formatName(output, event.loggerName());
		}

		/**
		 * Formats a logger name.
		 * @param output buffer.
		 * @param name logger name.
		 */
		public void formatName(StringBuilder output, String name);

		/**
		 * Default implementation that calls {@link LogEvent#loggerName()}.
		 * @return formatter.
		 */
		public static NameFormatter of() {
			return DefaultNameFormatter.INSTANT;
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
			return DefaultLevelFormatter.NO_PAD;
		}

		/**
		 * Default implementation calls {@link LevelFormatter#rightPadded(Level)}
		 * @return formatter.
		 */
		public static LevelFormatter ofRightPadded() {
			return DefaultLevelFormatter.RIGHT_PAD;
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
				case OFF -> /*    */ "TRACE";
				case TRACE -> /*  */ "TRACE";
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
			return DefaultInstantFormatter.INSTANT;
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
			var t = event.throwable();
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
			t.printStackTrace(new PrintWriter(new StringWriter(b)));
		}

	}

	/**
	 * Formats key values.
	 */
	public non-sealed interface KeyValuesFormatter extends LogFormatter {

		/**
		 * Format key values.
		 * @param output buffer.
		 * @param keyValues kvs.
		 */
		void formatKeyValues(StringBuilder output, KeyValues keyValues);

		@Override
		default void format(StringBuilder output, LogEvent event) {
			formatKeyValues(output, event.keyValues());
		}

		/**
		 * Creates a formatter that will print the key values in order of the passed in
		 * keys if they exist in percent encoding (RFC 3986 URI aka the format usually
		 * used in {@link URI#getQuery()}).
		 * @param keys keys where order is important.
		 * @return formatter.
		 */
		public static KeyValuesFormatter of(List<String> keys) {
			if (keys.isEmpty()) {
				return NoopFormatter.INSTANCE;
			}
			return new ListKeyValuesFormatter(keys);
		}

		/**
		 * Creates a formatter that will print the key values by percent encoding (RFC
		 * 3986 URI aka the format usually used in {@link URI#getQuery()}).
		 * @return formatter.
		 */
		public static KeyValuesFormatter of() {
			return DefaultKeyValuesFormatter.INSTANCE;
		}

	}

	/**
	 * Formats a thread.
	 */
	public non-sealed interface ThreadFormatter extends LogFormatter {

		/**
		 * Formats a thread.
		 * @param output buffer.
		 * @param threadName {@link LogEvent#threadName()}.
		 * @param threadId {@link LogEvent#threadId()}.
		 */
		void formatThread(StringBuilder output, String threadName, long threadId);

		@Override
		default void format(StringBuilder output, LogEvent event) {
			formatThread(output, event.threadName(), event.threadId());
		}

		/**
		 * Default thread formatter prints the the {@link Thread#getName()}.
		 * @return thread formatter.
		 */
		public static ThreadFormatter of() {
			return DefaultThreadFormatter.INSTANT;
		}

	}

	/**
	 * Tests if the log formatter is noop or is null which will be considered as noop.
	 * @param logFormatter formatter which <strong>can be <code>null</code></strong>!
	 * @return true if the formatter should not be used.
	 */
	public static boolean isNoop(@Nullable LogFormatter logFormatter) {
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
	public static void padRight(StringBuilder sb, String s, int n) {
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
	public static void padLeft(StringBuilder sb, String s, int n) {
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
			sbuf.append(DefaultEventFormatter.SPACES[5]);
			l -= 32;
		}

		for (int i = 4; i >= 0; i--) {
			if ((l & (1 << i)) != 0) {
				sbuf.append(DefaultEventFormatter.SPACES[i]);
			}
		}
	}

	/**
	 * A special formatter that will do nothing.
	 */
	enum NoopFormatter implements TimestampFormatter, ThrowableFormatter, KeyValuesFormatter, LevelFormatter,
			NameFormatter, ThreadFormatter {

		/**
		 * instance.
		 */
		INSTANCE;

		@Override
		public void formatKeyValues(StringBuilder output, KeyValues keyValues) {
		}

		@Override
		public void formatThrowable(StringBuilder output, Throwable throwable) {
		}

		@Override
		public void formatTimestamp(StringBuilder output, Instant instant) {
		}

		@Override
		public void formatName(StringBuilder output, String name) {
		}

		@Override
		public void formatLevel(StringBuilder output, Level level) {
		}

		@Override
		public void formatThread(StringBuilder output, String threadName, long threadId) {
		}

		@Override
		public void format(StringBuilder output, LogEvent event) {
		}

	}

}

record DefaultEventFormatter(LogFormatter[] formatters) implements EventFormatter {

	static String[] SPACES = { " ", "  ", "    ", "        ", // 1,2,4,8 spaces
			"                ", // 16 spaces
			"                                " }; // 32 spaces

	@Override
	public void format(StringBuilder output, LogEvent event) {
		for (var formatter : formatters) {
			formatter.format(output, event);
		}
	}

}

enum DefaultMessageFormatter implements MessageFormatter {

	INSTANT;

	@Override
	public void formatMessage(StringBuilder output, LogEvent event) {
		event.formattedMessage(output);
	}

}

enum DefaultNameFormatter implements NameFormatter {

	INSTANT;

	@Override
	public void formatName(StringBuilder output, String name) {
		output.append(name);
	}

}

enum DefaultLevelFormatter implements LevelFormatter {

	NO_PAD {
		@Override
		public void formatLevel(StringBuilder output, Level level) {
			output.append(LevelFormatter.toString(level));
		}
	},
	RIGHT_PAD {
		@Override
		public void formatLevel(StringBuilder output, Level level) {
			output.append(LevelFormatter.rightPadded(level));
		}
	}

}

enum DefaultThreadFormatter implements ThreadFormatter {

	INSTANT;

	@Override
	public void formatThread(StringBuilder output, String threadName, long threadId) {
		output.append(threadName);
	}

}

enum DefaultInstantFormatter implements TimestampFormatter {

	INSTANT;

	private static final DateTimeFormatter formatter = DateTimeFormatter.ofPattern(TTLL_TIME_FORMAT)
		.withZone(ZoneId.from(ZoneOffset.UTC));

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

enum DefaultKeyValuesFormatter implements KeyValuesFormatter, KeyValuesConsumer<StringBuilder> {

	INSTANCE;

	@Override
	public void formatKeyValues(StringBuilder output, KeyValues keyValues) {
		keyValues.forEach(this, 0, output);
	}

	static void formatKeyValue(StringBuilder output, String k, String v) {
		PercentCodec.encode(output, k, StandardCharsets.UTF_8);
		output.append("=");
		PercentCodec.encode(output, v, StandardCharsets.UTF_8);
	}

	@Override
	public int accept(KeyValues values, String key, String value, int index, StringBuilder storage) {
		if (index > 0) {
			storage.append("&");
		}
		formatKeyValue(storage, key, value);
		return index + 1;
	}

}

final class ListKeyValuesFormatter implements KeyValuesFormatter {

	private final String[] keys;

	ListKeyValuesFormatter(List<String> keys) {
		var ks = List.copyOf(keys);
		this.keys = ks.toArray(new String[] {});
	}

	@Override
	public void formatKeyValues(StringBuilder output, KeyValues keyValues) {
		boolean first = true;
		for (String k : keys) {
			String v = keyValues.getValue(k);
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

class StringWriter extends Writer {

	private final StringBuilder buf;

	/**
	 * Create a new string writer using the default initial string-buffer size.
	 */
	StringWriter(StringBuilder buf) {
		this.buf = buf;
		lock = buf;
	}

	/**
	 * Write a single character.
	 */
	public void write(int c) {
		buf.append((char) c);
	}

	public void write(char cbuf[], int off, int len) {
		if ((off < 0) || (off > cbuf.length) || (len < 0) || ((off + len) > cbuf.length) || ((off + len) < 0)) {
			throw new IndexOutOfBoundsException();
		}
		else if (len == 0) {
			return;
		}
		buf.append(cbuf, off, len);
	}

	/**
	 * Write a string.
	 */
	public void write(String str) {
		buf.append(str);
	}

	public void write(String str, int off, int len) {
		buf.append(str, off, off + len);
	}

	public StringWriter append(CharSequence csq) {
		write(String.valueOf(csq));
		return this;
	}

	public StringWriter append(CharSequence csq, int start, int end) {
		if (csq == null)
			csq = "null";
		return append(csq.subSequence(start, end));
	}

	public StringWriter append(char c) {
		write(c);
		return this;
	}

	/**
	 * Return the string buffer itself.
	 * @return StringBuffer holding the current buffer value.
	 */
	public StringBuilder getBuffer() {
		return buf;
	}

	public void flush() {
	}

	/**
	 * Closing a {@code StringWriter} has no effect. The methods in this class can be
	 * called after the stream has been closed without generating an {@code IOException}.
	 */
	public void close() throws IOException {
	}

}

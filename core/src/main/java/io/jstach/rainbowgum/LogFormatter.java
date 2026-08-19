package io.jstach.rainbowgum;

import java.io.PrintWriter;
import java.lang.System.Logger.Level;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicReference;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

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
			formatters.add(TimestampFormatter.of(dateTimeFormatter));
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
		 * {@link URI#getQuery()}). Keys that are mapped to <code>null</code> will only
		 * have the key printed and no separating equal sign (<code>=</code>). This is to
		 * differentiate empty string and <code>null</code>.
		 * @return formatter.
		 */
		public Builder keyValues() {
			return add(DefaultKeyValuesFormatter.INSTANCE);
		}

		/**
		 * Creates a formatter that will print the key values in order of the passed in
		 * keys if they exist in percent encoding (RFC 3986 URI aka the format usually
		 * used in {@link URI#getQuery()}). <strong>An empty list is considered a noop and
		 * no keys will be ommitted!</strong> If you want to all keys use
		 * {@link #keyValues()}.
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
		public Builder keyValue(String key, @Nullable String fallback) {
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
		 * Appends the events throwable stack trace using a custom
		 * {@link ThrowableFormatter} (e.g. one created with
		 * {@link ThrowableFormatter#builder()}).
		 * @param throwableFormatter formatter to use.
		 * @return this.
		 */
		public Builder throwable(ThrowableFormatter throwableFormatter) {
			formatters.add(throwableFormatter);
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

		/**
		 * Creates the formatter and converts it to an encoder.
		 * @return encoder.
		 * @apiNote for ergonomics
		 */
		public LogEncoder encoder() {
			return LogEncoder.of(build());
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
	public sealed interface LevelFormatter extends LogFormatter {

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
		 * Formats a level.
		 * @param level level
		 * @return formatted level as a string.
		 */
		default String formatLevel(Level level) {
			StringBuilder sb = new StringBuilder();
			formatLevel(sb, level);
			return sb.toString();
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
		 * {@link Level#ALL} is "<code>TRACE</code>", {@link Level#OFF} is
		 * "<code>ERROR</code>" and {@link Level#WARNING} is "<code>WARN</code>".
		 * @param level system logger level.
		 * @return upper case string of level.
		 */
		public static String toString(Level level) {
			return switch (level) {
				case DEBUG -> "DEBUG";
				case ALL -> "TRACE";
				case ERROR -> "ERROR";
				case INFO -> "INFO";
				case OFF -> "ERROR";
				case TRACE -> "TRACE";
				case WARNING -> "WARN";
			};
		}

		/**
		 * Turns a Level into a SLF4J like level String that is all upper case and same
		 * length with right padding. {@link Level#ALL} is "<code>TRACE</code>",
		 * {@link Level#OFF} is "<code>ERROR</code>" and {@link Level#WARNING} is
		 * "<code>WARN</code>".
		 * @param level system logger level.
		 * @return upper case string of level.
		 */
		public static String rightPadded(Level level) {
			return switch (level) {
				case DEBUG -> /*   */ "DEBUG";
				case ALL -> /*     */ "TRACE";
				case ERROR -> /*   */ "ERROR";
				case INFO -> /*    */ "INFO ";
				case OFF -> /*     */ "ERROR";
				case TRACE -> /*   */ "TRACE";
				case WARNING -> /* */ "WARN ";
			};
		}

	}

	/**
	 * Formats event timestamps.
	 */
	public sealed interface TimestampFormatter extends LogFormatter {

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
		 * Formats a timestamp using ISO format at fixed millisecond precision (e.g.
		 * {@code 2023-11-14T22:13:20.123Z}, always exactly 3 fractional digits). Pair
		 * with {@link #ofMicros()} to append additional sub-millisecond digits, similar
		 * to Logback's {@code %d{ISO8601}%microsecond}.
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
			return of(dateTimeFormatter, false);
		}

		/**
		 * Formats a timestamp using standard JDK date time formatter.
		 * <p>
		 * Formatting with {@link DateTimeFormatter} is comparatively expensive to do on
		 * every event, so if the caller knows the formatter's finest resolution is
		 * milliseconds (e.g. a pattern using at most {@code SSS}, never
		 * {@code n}/{@code N} nanosecond fields), {@code cacheMillisecondPrecision} can
		 * be set to {@code true} to reuse the previously formatted string whenever
		 * consecutive events land in the same millisecond - the same trick Logback's
		 * {@code CachingDateFormatter} and Log4j2's {@code FixedDateFormat} use. Passing
		 * {@code true} for a formatter that can actually render sub-millisecond precision
		 * will silently truncate that precision, so only pass {@code true} when the
		 * pattern is known to not exceed millisecond resolution.
		 * @param dateTimeFormatter date time formatter.
		 * @param cacheMillisecondPrecision {@code true} if the formatter's output is
		 * fully determined by the instant's millisecond value.
		 * @return timestamp formatter.
		 */
		public static TimestampFormatter of(DateTimeFormatter dateTimeFormatter, boolean cacheMillisecondPrecision) {
			return new DateTimeFormatterInstantFormatter(dateTimeFormatter, cacheMillisecondPrecision);
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
		 * This formatter is equivalent to {@code of(Integer.MAX_VALUE, List.of())} except
		 * that it defers entirely to {@link Throwable#printStackTrace()} and thus will
		 * respect any override of that method a particular {@link Throwable} subclass
		 * might have.
		 * @return formatter.
		 */
		public static ThrowableFormatter of() {
			return DefaultThrowableFormatter.INSTANT;
		}

		/**
		 * Creates a builder for a throwable formatter that can limit the number of stack
		 * frame lines shown for each throwable in the cause/suppressed chain, exclude
		 * frames matching regular expressions, and append packaging data. The output is
		 * otherwise formatted like {@link Throwable#printStackTrace()}: common frames
		 * shared with the enclosing throwable are elided the same way (e.g.
		 * <code>"... 6 more"</code>) as long as the depth limit was not what stopped
		 * printing; if the depth limit is what stopped printing a
		 * <code>"... N frames truncated"</code> line is emitted instead.
		 * @return builder.
		 * @apiNote a builder left at its defaults (no max lines, exclusions, or packaging
		 * data) builds {@link #of()} itself; otherwise the built formatter walks
		 * {@link Throwable#getCause()}/{@link Throwable#getSuppressed()}/
		 * {@link Throwable#getStackTrace()} directly instead of calling
		 * {@link Throwable#printStackTrace()} and thus will not honor a custom override
		 * of that method.
		 */
		public static Builder builder() {
			return new Builder();
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

		/**
		 * Builds a configurable {@link ThrowableFormatter}.
		 *
		 * @see ThrowableFormatter#builder()
		 */
		public final class Builder {

			private int maxLines = Integer.MAX_VALUE;

			private List<String> excludes = List.of();

			private boolean packagingData = false;

			private Builder() {
			}

			/**
			 * Maximum number of stack frame ("at ...") lines printed per throwable in the
			 * chain. Use {@link Integer#MAX_VALUE} for no limit (the default, and matches
			 * {@link ThrowableFormatter#of()} for well behaved throwables). {@code 0}
			 * prints just the throwable header lines (class and message) for the whole
			 * chain with no frames at all.
			 * @param maxLines maximum stack frame lines per throwable.
			 * @return this builder.
			 */
			public Builder maxLines(int maxLines) {
				this.maxLines = maxLines;
				return this;
			}

			/**
			 * Regular expressions matched with {@link Matcher#find()} against each
			 * frame's {@link StackTraceElement#toString()}. A frame matching any of these
			 * is omitted entirely and does not count against {@link #maxLines(int)}.
			 * Defaults to empty (no filtering).
			 * @param excludes exclude patterns.
			 * @return this builder.
			 */
			public Builder excludes(List<String> excludes) {
				this.excludes = excludes;
				return this;
			}

			/**
			 * Whether to append packaging data (the jar or module a frame's class was
			 * loaded from, and its version) after each frame line, e.g.
			 * <code>[myapp-1.0.jar:1.0]</code> or <code>[java.base:na]</code>. This
			 * mirrors (but does not byte-for-byte match) logback's
			 * <code>ExtendedThrowableProxyConverter</code> output. Resolution requires
			 * loading the frame's class (without initializing it) and can fail silently
			 * to <code>[na:na]</code> for frames whose class cannot be loaded (e.g.
			 * dynamically generated classes). This has a real per-frame cost so it
			 * defaults to <code>false</code>.
			 * @param packagingData true to append packaging data.
			 * @return this builder.
			 */
			public Builder packagingData(boolean packagingData) {
				this.packagingData = packagingData;
				return this;
			}

			/**
			 * Builds the formatter. If none of {@link #maxLines(int)},
			 * {@link #excludes(List)}, or {@link #packagingData(boolean)} were set away
			 * from their defaults, returns {@link ThrowableFormatter#of()} (i.e. plain
			 * {@link Throwable#printStackTrace()} behavior) instead of constructing a
			 * formatter that walks the throwable manually for no reason.
			 * @return formatter.
			 */
			public ThrowableFormatter build() {
				if (maxLines == Integer.MAX_VALUE && excludes.isEmpty() && !packagingData) {
					return ThrowableFormatter.of();
				}
				List<Pattern> compiled = excludes.isEmpty() ? List.of()
						: excludes.stream().map(Pattern::compile).toList();
				var resolver = packagingData ? new PackagingDataResolver() : null;
				return new StandardThrowableFormatter(maxLines, compiled, resolver);
			}

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

@SuppressWarnings("ArrayRecordComponent")
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
	/*
	 * appendInstant(3) renders a fixed-width 3-digit millisecond fraction (unlike
	 * DateTimeFormatter.ISO_DATE_TIME, which renders a variable-width fraction of 0-9
	 * digits based on the instant's actual sub-millisecond value) - fixed width matches
	 * Logback/Log4j2's ISO output and is what makes this safe to millis-cache.
	 */
	ISO(new DateTimeFormatterBuilder().appendInstant(3).toFormatter()), MICROS(DateTimeFormatter.ISO_DATE_TIME) {
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

	final DateTimeFormatter formatter;

	private final MillisCache cache = new MillisCache();

	DefaultInstantFormatter(DateTimeFormatter formatter) {
		this.formatter = formatter;
	}

	@Override
	public void formatTimestamp(StringBuilder output, Instant instant) {
		cache.formatTimestamp(output, instant, formatter);
	}

}

final class DateTimeFormatterInstantFormatter implements TimestampFormatter {

	private final DateTimeFormatter dateTimeFormatter;

	private final @Nullable MillisCache cache;

	DateTimeFormatterInstantFormatter(DateTimeFormatter dateTimeFormatter, boolean cacheMillisecondPrecision) {
		this.dateTimeFormatter = dateTimeFormatter;
		this.cache = cacheMillisecondPrecision ? new MillisCache() : null;
	}

	@Override
	public void formatTimestamp(StringBuilder output, Instant instant) {
		var c = cache;
		if (c == null) {
			dateTimeFormatter.formatTo(instant, output);
			return;
		}
		c.formatTimestamp(output, instant, dateTimeFormatter);
	}

}

/**
 * Caches a formatted timestamp string keyed by the instant's millisecond value, the same
 * trick Logback's {@code CachingDateFormatter} and Log4j2's {@code FixedDateFormat} use -
 * reformatting only happens when consecutive events land in different milliseconds. Only
 * correct for formatters whose output is fully determined by millisecond precision
 * (callers are responsible for that guarantee).
 */
final class MillisCache {

	private record Entry(long millis, String formatted) {
	}

	private static final Entry EMPTY = new Entry(Long.MIN_VALUE, "");

	private final AtomicReference<Entry> ref = new AtomicReference<>(EMPTY);

	void formatTimestamp(StringBuilder output, Instant instant, DateTimeFormatter formatter) {
		long millis = instant.toEpochMilli();
		Entry cached = ref.get();
		if (cached.millis() == millis) {
			output.append(cached.formatted());
			return;
		}
		StringBuilder sb = new StringBuilder(32);
		formatter.formatTo(instant, sb);
		String formatted = sb.toString();
		ref.set(new Entry(millis, formatted));
		output.append(formatted);
	}

}

enum DefaultThrowableFormatter implements ThrowableFormatter {

	INSTANT;

	@Override
	public void formatThrowable(StringBuilder output, Throwable throwable) {
		ThrowableFormatter.appendThrowable(output, throwable);
	}

}

/**
 * Reimplements {@link Throwable#printStackTrace()}'s algorithm (header line, frames,
 * common-frame elision against the enclosing trace, "Caused by:"/"Suppressed:" sections,
 * circular reference guard) so that a max line count and frame exclusion patterns can be
 * applied while printing.
 */
final class StandardThrowableFormatter implements ThrowableFormatter {

	private static final String CAUSE_CAPTION = "Caused by: ";

	private static final String SUPPRESSED_CAPTION = "Suppressed: ";

	private final int maxLines;

	private final List<Pattern> excludes;

	private final @Nullable PackagingDataResolver packagingData;

	StandardThrowableFormatter(int maxLines, List<Pattern> excludes, @Nullable PackagingDataResolver packagingData) {
		this.maxLines = maxLines;
		this.excludes = excludes;
		this.packagingData = packagingData;
	}

	@Override
	public void formatThrowable(StringBuilder output, Throwable throwable) {
		Set<Throwable> dejaVu = Collections.newSetFromMap(new IdentityHashMap<>());
		dejaVu.add(throwable);
		var trace = throwable.getStackTrace();
		output.append(throwable).append(System.lineSeparator());
		printFrames(output, trace, trace.length, "");
		for (var suppressed : throwable.getSuppressed()) {
			printEnclosed(output, suppressed, trace, SUPPRESSED_CAPTION, "\t", dejaVu);
		}
		var cause = throwable.getCause();
		if (cause != null) {
			printEnclosed(output, cause, trace, CAUSE_CAPTION, "", dejaVu);
		}
	}

	private void printEnclosed(StringBuilder output, Throwable throwable, StackTraceElement[] enclosingTrace,
			String caption, String prefix, Set<Throwable> dejaVu) {
		if (!dejaVu.add(throwable)) {
			output.append(prefix)
				.append("[CIRCULAR REFERENCE: ")
				.append(throwable)
				.append(']')
				.append(System.lineSeparator());
			return;
		}
		var trace = throwable.getStackTrace();
		int m = trace.length - 1;
		int n = enclosingTrace.length - 1;
		while (m >= 0 && n >= 0 && trace[m].equals(enclosingTrace[n])) {
			m--;
			n--;
		}
		int framesInCommon = trace.length - 1 - m;
		output.append(prefix).append(caption).append(throwable).append(System.lineSeparator());
		boolean truncated = printFrames(output, trace, m + 1, prefix);
		if (!truncated && framesInCommon > 0) {
			output.append(prefix)
				.append("\t... ")
				.append(framesInCommon)
				.append(" more")
				.append(System.lineSeparator());
		}
		for (var suppressed : throwable.getSuppressed()) {
			printEnclosed(output, suppressed, trace, SUPPRESSED_CAPTION, prefix + "\t", dejaVu);
		}
		var cause = throwable.getCause();
		if (cause != null) {
			printEnclosed(output, cause, trace, CAUSE_CAPTION, prefix, dejaVu);
		}
	}

	/*
	 * Prints frames trace[0..count) filtered by excludes and capped at maxLines. Returns
	 * true if there were excluded-filtered frames beyond maxLines (i.e. the output was
	 * cut short because of maxLines rather than ending naturally).
	 */
	private boolean printFrames(StringBuilder output, StackTraceElement[] trace, int count, String prefix) {
		int printed = 0;
		int available = 0;
		for (int i = 0; i < count; i++) {
			var element = trace[i];
			if (isExcluded(element)) {
				continue;
			}
			available++;
			if (printed < maxLines) {
				output.append(prefix).append("\tat ").append(element);
				if (packagingData != null) {
					output.append(' ').append(packagingData.resolve(element));
				}
				output.append(System.lineSeparator());
				printed++;
			}
		}
		if (available > maxLines) {
			output.append(prefix)
				.append("\t... ")
				.append(available - maxLines)
				.append(" frames truncated")
				.append(System.lineSeparator());
			return true;
		}
		return false;
	}

	private boolean isExcluded(StackTraceElement element) {
		if (excludes.isEmpty()) {
			return false;
		}
		String s = element.toString();
		for (var p : excludes) {
			Matcher matcher = p.matcher(s);
			if (matcher.find()) {
				return true;
			}
		}
		return false;
	}

}

/**
 * Resolves and caches which jar/module/directory a stack frame's class was loaded from
 * and its reported version, formatted as <code>[location:version]</code>. Named modules
 * (including JDK frames) are resolved for free from the frame itself or the loaded
 * class's {@link Module} without touching a {@link java.security.CodeSource}. Classpath
 * (unnamed module) frames fall back to the class's {@link java.security.CodeSource} and
 * {@link Package#getImplementationVersion()}. Either "location" or "version" is
 * <code>na</code> when unknown, and the whole result is <code>[na:na]</code> when the
 * frame's class cannot be loaded at all (e.g. dynamically generated classes).
 */
final class PackagingDataResolver {

	private final ConcurrentMap<String, String> cache = new ConcurrentHashMap<>();

	String resolve(StackTraceElement element) {
		String className = element.getClassName();
		String cached = cache.get(className);
		if (cached != null) {
			return cached;
		}
		String computed = compute(element);
		cache.put(className, computed);
		return computed;
	}

	private static String compute(StackTraceElement element) {
		String moduleName = element.getModuleName();
		if (moduleName != null) {
			return format(moduleName, element.getModuleVersion());
		}
		Class<?> type = load(element.getClassName());
		if (type == null) {
			return "[na:na]";
		}
		Module module = type.getModule();
		if (module.isNamed()) {
			var descriptor = module.getDescriptor();
			String version = descriptor == null ? null : descriptor.rawVersion().orElse(null);
			return format(module.getName(), version);
		}
		var codeSource = type.getProtectionDomain().getCodeSource();
		if (codeSource == null) {
			return "[na:na]";
		}
		var location = codeSource.getLocation();
		if (location == null) {
			return "[na:na]";
		}
		String path = location.getPath();
		if (path == null || path.isEmpty()) {
			path = location.toString();
		}
		if (path.endsWith("/")) {
			path = path.substring(0, path.length() - 1);
		}
		int slash = path.lastIndexOf('/');
		String name = slash >= 0 && slash < path.length() - 1 ? path.substring(slash + 1) : path;
		var pkg = type.getPackage();
		String version = pkg == null ? null : pkg.getImplementationVersion();
		return format(name, version);
	}

	private static String format(String location, @Nullable String version) {
		return "[" + location + ":" + (version == null || version.isEmpty() ? "na" : version) + "]";
	}

	private static @Nullable Class<?> load(String className) {
		var contextLoader = Thread.currentThread().getContextClassLoader();
		if (contextLoader != null) {
			var type = loadOrNull(className, contextLoader);
			if (type != null) {
				return type;
			}
		}
		return loadOrNull(className, PackagingDataResolver.class.getClassLoader());
	}

	private static @Nullable Class<?> loadOrNull(String className, @Nullable ClassLoader loader) {
		try {
			return Class.forName(className, false, loader);
		}
		catch (ClassNotFoundException | LinkageError | SecurityException e) {
			return null;
		}
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

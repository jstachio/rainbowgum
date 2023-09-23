package io.jstach.rainbowgum;

import java.io.IOException;
import java.io.PrintWriter;
import java.io.Writer;
import java.lang.System.Logger.Level;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

import io.jstach.rainbowgum.KeyValues.KeyValuesConsumer;
import io.jstach.rainbowgum.LogFormatter.EventFormatter;
import io.jstach.rainbowgum.LogFormatter.InstantFormatter;
import io.jstach.rainbowgum.LogFormatter.KeyValuesFormatter;
import io.jstach.rainbowgum.LogFormatter.LevelFormatter;
import io.jstach.rainbowgum.LogFormatter.NameFormatter;
import io.jstach.rainbowgum.LogFormatter.ThreadFormatter;
import io.jstach.rainbowgum.LogFormatter.ThrowableFormatter;

public sealed interface LogFormatter {

	public void format(StringBuilder output, LogEvent event);

	public static EventFormatter.Builder builder() {
		return EventFormatter.builder();
	}

	default boolean isNoop() {
		return isNoop(this);
	}

	public static NoopFormatter noop() {
		return NoopFormatter.INSTANT;
	}

	public record StaticFormatter(String content) implements LogFormatter {
		@Override
		public void format(StringBuilder output, LogEvent event) {
			output.append(content);
		}
	}

	@FunctionalInterface
	public non-sealed interface EventFormatter extends LogFormatter {

		public void format(StringBuilder output, LogEvent event);

		public static EventFormatter of(List<LogFormatter> formatters) {
			return new DefaultEventFormatter(formatters);
		}

		public static Builder builder() {
			return new Builder();
		}

		public static class Builder {

			private List<LogFormatter> formatters = new ArrayList<>();

			private Builder() {
			}

			public Builder level() {
				formatters.add(LogFormatter.LevelFormatter.of());
				return this;
			}
			
			public Builder loggerName() {
				formatters.add(LogFormatter.NameFormatter.of());
				return this;
			}
			
			public Builder add(LogFormatter formatter) {
				formatters.add(formatter);
				return this;
			}

			public Builder add(String content) {
				formatters.add(new StaticFormatter(content));
				return this;
			}

			public EventFormatter build() {
				return EventFormatter.of(formatters);
			}

		}

	}

	public non-sealed interface NameFormatter extends LogFormatter {

		@Override
		default void format(StringBuilder output, LogEvent event) {
			output.append(formatName(event.loggerName()));
		}

		public String formatName(String name);

		public static NameFormatter of() {
			return DefaultNameFormatter.INSTANT;
		}

	}

	public non-sealed interface LevelFormatter extends LogFormatter {

		String format(Level level);

		@Override
		default void format(StringBuilder output, LogEvent event) {
			output.append(format(event.level()));

		}

		public static LevelFormatter of() {
			return DefaultLevelFormatter.INSTANT;
		}

	}

	public non-sealed interface InstantFormatter extends LogFormatter {

		String format(Instant instant);

		@Override
		default void format(StringBuilder output, LogEvent event) {
			output.append(format(event.timeStamp()));
		}

		public static InstantFormatter of() {
			return DefaultInstantFormatter.INSTANT;
		}

	}

	public non-sealed interface ThrowableFormatter extends LogFormatter {

		void format(StringBuilder output, Throwable throwable);

		@Override
		default void format(StringBuilder output, LogEvent event) {
			var t = event.throwable();
			if (t != null) {
				format(output, t);
			}
		}

		public static ThrowableFormatter of() {
			return DefaultThrowableFormatter.INSTANT;
		}

		public static void append(StringBuilder b, Throwable t) {
			/*
			 * TODO optimize
			 */
			t.printStackTrace(new PrintWriter(new StringWriter(b)));
		}

	}

	public non-sealed interface KeyValuesFormatter extends LogFormatter {

		void format(StringBuilder output, KeyValues keyValues);

		@Override
		default void format(StringBuilder output, LogEvent event) {
			format(output, event.keyValues());
		}

		public static KeyValuesFormatter of(List<String> keys) {
			if (keys.isEmpty()) {
				return NoopFormatter.INSTANT;
			}
			return new ListKeyValuesFormatter(keys);
		}

		public static KeyValuesFormatter of() {
			return DefaultKeyValuesFormatter.INSTANCE;
		}

		public static KeyValuesFormatter noop() {
			return NoopFormatter.INSTANT;
		}

	}

	public non-sealed interface ThreadFormatter extends LogFormatter {

		String formatThread(String threadName);

		@Override
		default void format(StringBuilder output, LogEvent event) {
			output.append(formatThread(event.threadName()));
		}

		public static ThreadFormatter of() {
			return DefaultThreadFormatter.INSTANT;
		}

	}

	public static boolean isNoop(LogFormatter logFormatter) {
		return NoopFormatter.INSTANT == logFormatter;
	}

	public enum NoopFormatter implements InstantFormatter, ThrowableFormatter, KeyValuesFormatter, LevelFormatter,
			NameFormatter, ThreadFormatter {

		INSTANT;

		@Override
		public void format(StringBuilder output, KeyValues keyValues) {
		}

		@Override
		public void format(StringBuilder output, Throwable throwable) {
		}

		@Override
		public String format(Instant instant) {
			return "";
		}

		@Override
		public String formatName(String name) {
			return "";
		}

		@Override
		public String format(Level level) {
			return "";
		}

		@Override
		public String formatThread(String threadName) {
			return "";
		}

		@Override
		public void format(StringBuilder output, LogEvent event) {
		}

	}

	public static String padRight(String s, int n) {
		return String.format("%-" + n + "s", s.substring(0, Math.min(s.length(), n)));
	}

}

record DefaultEventFormatter(List<LogFormatter> formatters) implements EventFormatter {

	@Override
	public void format(StringBuilder output, LogEvent event) {
		for (var formatter : formatters) {
			formatter.format(output, event);
		}
	}

}

enum DefaultNameFormatter implements NameFormatter {

	INSTANT;

	@Override
	public String formatName(String name) {
		return name;
	}

}

enum DefaultLevelFormatter implements LevelFormatter {

	INSTANT;

	@Override
	public String format(Level level) {
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

}

enum DefaultThreadFormatter implements ThreadFormatter {

	INSTANT;

	@Override
	public String formatThread(String threadName) {
		return LogFormatter.padRight(threadName, 12);
	}

}

enum DefaultInstantFormatter implements InstantFormatter {

	INSTANT;

	private static final DateTimeFormatter formatter = DateTimeFormatter.ofPattern("HH:mm:ss.SSS")
		.withZone(ZoneId.from(ZoneOffset.UTC));

	@Override
	public String format(Instant instant) {
		return formatter.format(instant);
	}

}

enum DefaultThrowableFormatter implements ThrowableFormatter {

	INSTANT;

	@Override
	public void format(StringBuilder output, Throwable throwable) {
		ThrowableFormatter.append(output, throwable);
	}

}

enum DefaultKeyValuesFormatter implements KeyValuesFormatter, KeyValuesConsumer<StringBuilder> {

	INSTANCE;

	@Override
	public void format(StringBuilder output, KeyValues keyValues) {
		keyValues.forEach(this, 0, output);
	}

	static void formatKeyValue(StringBuilder output, String k, String v) {
		output.append(URLEncoder.encode(k, StandardCharsets.US_ASCII));
		output.append("=");
		output.append(URLEncoder.encode(v, StandardCharsets.US_ASCII));
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
	public void format(StringBuilder output, KeyValues keyValues) {
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
	public StringWriter(StringBuilder buf) {
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

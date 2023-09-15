package io.jstach.rainbowgum;

import java.lang.System.Logger.Level;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Collection;
import java.util.List;
import java.util.Map;

import io.jstach.rainbowgum.LogFormatter.InstantFormatter;
import io.jstach.rainbowgum.LogFormatter.KeyValuesFormatter;
import io.jstach.rainbowgum.LogFormatter.LevelFormatter;
import io.jstach.rainbowgum.LogFormatter.NameFormatter;
import io.jstach.rainbowgum.LogFormatter.ThreadFormatter;
import io.jstach.rainbowgum.LogFormatter.ThrowableFormatter;

public sealed interface LogFormatter {

	default boolean isNoop() {
		return isNoop(this);
	}

	public static NoopFormatter noop() {
		return NoopFormatter.INSTANT;
	}

	public non-sealed interface EventFormatter extends LogFormatter {

		public void format(LogOutput output, LogEvent event);

	}

	public non-sealed interface NameFormatter extends LogFormatter {

		public String formatName(String name);

		public static NameFormatter of() {
			return DefaultFormatter.INSTANT;
		}

	}

	public non-sealed interface LevelFormatter extends LogFormatter {

		String format(Level level);

		public static LevelFormatter of() {
			return DefaultFormatter.INSTANT;
		}

	}

	public non-sealed interface InstantFormatter extends LogFormatter {

		String format(Instant instant);

		public static InstantFormatter of() {
			return DefaultFormatter.INSTANT;
		}

	}

	public non-sealed interface ThrowableFormatter extends LogFormatter {

		void format(LogOutput output, Throwable throwable);

		public static ThrowableFormatter of() {
			return DefaultFormatter.INSTANT;
		}

	}

	public non-sealed interface KeyValuesFormatter extends LogFormatter {

		void format(LogOutput output, Map<String, String> keyValues);

		public static KeyValuesFormatter of(List<String> keys) {
			if (keys.isEmpty()) {
				return NoopFormatter.INSTANT;
			}
			return new DefaultKeyValuesFormatter(keys);
		}

		public static KeyValuesFormatter of() {
			return NoopFormatter.INSTANT;
		}

	}

	public non-sealed interface ThreadFormatter extends LogFormatter {

		String formatThread(String threadName);

		public static ThreadFormatter of() {
			return DefaultFormatter.INSTANT;
		}

	}

	public static boolean isNoop(LogFormatter logFormatter) {
		return NoopFormatter.INSTANT == logFormatter;
	}

	public enum NoopFormatter implements InstantFormatter, ThrowableFormatter, KeyValuesFormatter, LevelFormatter,
			NameFormatter, ThreadFormatter {

		INSTANT;

		@Override
		public void format(LogOutput output, Map<String, String> keyValues) {
		}

		@Override
		public void format(LogOutput output, Throwable throwable) {
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

	}

	public static String padRight(String s, int n) {
		return String.format("%-" + n + "s", s.substring(0, Math.min(s.length(), n)));
	}

}

enum DefaultFormatter implements NameFormatter, LevelFormatter, ThreadFormatter, InstantFormatter, ThrowableFormatter {

	INSTANT;

	@Override
	public String formatName(String name) {
		return name;
	}

	@Override
	public String formatThread(String threadName) {
		return LogFormatter.padRight(threadName, 12);
	}

	private static final DateTimeFormatter formatter = DateTimeFormatter.ofPattern("HH:mm:ss.SSS")
		.withZone(ZoneId.from(ZoneOffset.UTC));

	@Override
	public String format(Instant instant) {
		return formatter.format(instant);
	}

	@Override
	public void format(LogOutput output, Throwable throwable) {
		throwable.printStackTrace(output.asWriter());
	}

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

record DefaultKeyValuesFormatter(List<String> keys) implements KeyValuesFormatter {

	@Override
	public void format(LogOutput output, Map<String, String> keyValues) {
		if (!keys.isEmpty()) {
			Collection<String> ks;
			if (keys.size() == 1 && "*".equals(keys.get(0))) {
				ks = keyValues.keySet();
			}
			else {
				ks = keys;
			}
			boolean first = true;
			for (String k : ks) {
				String v = keyValues.get(k);
				if (v == null) {
					continue;
				}
				if (first) {
					first = false;
				}
				else {
					output.append("&");
				}
				output.append(URLEncoder.encode(k, StandardCharsets.US_ASCII));
				output.append("=");
				output.append(URLEncoder.encode(v, StandardCharsets.US_ASCII));

			}
		}

	}
}

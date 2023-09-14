package io.jstach.rainbowgum.jansi;

import java.lang.System.Logger.Level;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Map;

import org.eclipse.jdt.annotation.NonNull;
import org.eclipse.jdt.annotation.Nullable;
import org.fusesource.jansi.Ansi;
import org.fusesource.jansi.Ansi.Attribute;
import org.fusesource.jansi.Ansi.Color;

import io.jstach.rainbowgum.LogEvent;
import io.jstach.rainbowgum.LogFormatter;
import io.jstach.rainbowgum.LogOutput;

public class JansiLogFormatter implements LogFormatter {

	private final LevelFormatter levelFormatter;
	private final InstantFormatter instantFormatter;
	private final ThrowableFormatter throwableFormatter;
	private final boolean showThreadName;
	private final boolean levelInBrackets;
	private final boolean showShortLogName;
	private final boolean showLogName;
	private final List<String> mdcKeys;

	public JansiLogFormatter(
			LevelFormatter levelFormatter,
			InstantFormatter instantFormatter,
			ThrowableFormatter throwableFormatter,
			boolean showThreadName,
			boolean levelInBrackets,
			boolean showShortLogName,
			boolean showLogName,
			List<String> mdcKeys) {
		super();
		this.levelFormatter = levelFormatter;
		this.instantFormatter = instantFormatter;
		this.throwableFormatter = throwableFormatter;
		this.showThreadName = showThreadName;
		this.levelInBrackets = levelInBrackets;
		this.showShortLogName = showShortLogName;
		this.showLogName = showLogName;
		this.mdcKeys = mdcKeys;
	}

	public void format(
			LogOutput output,
			LogEvent logEvent) {

		var level = logEvent.level();
		var instant = logEvent.timeStamp();
		var shortLogName = logEvent.loggerShortName();
		var name = logEvent.loggerName();
		@Nullable
		Throwable t = logEvent.throwable();
		var formattedMessage = logEvent.formattedMessage();

		// StringBuilder buf = new StringBuilder(32);
		StringBuilder b = new StringBuilder(32);
		Ansi buf = Ansi.ansi(b);

		// Append date-time if so configured

		buf.fg(Color.CYAN);
		buf.a(getFormattedDate(instant));
		buf.fg(Color.DEFAULT);
		buf.a(' ');

		// Append current thread name if so configured
		if (showThreadName) {
			buf.a(Attribute.INTENSITY_FAINT);
			buf.a("[");
			buf.append(padRight(Thread.currentThread().getName(), 12)).append("]");
			buf.a(Attribute.RESET);
			buf.a(" ");
			// buf.append('[');
			// buf.append(
			//
			// buf.append("] ");
		}

		if (levelInBrackets)
			buf.append('[');

		// Append a readable representation of the log level

		colorLevel(buf, level);

		if (levelInBrackets)
			buf.append(']');
		buf.append(' ');

		// Append the name of the log instance if so configured
		if (showShortLogName) {
			buf.append(String.valueOf(shortLogName));
		} else if (showLogName) {
			buf.fg(Color.MAGENTA);
			buf.a(String.valueOf(name));
		}

		Map<String, @Nullable String> m = logEvent.getKeyValues();
		List<String> keys = mdcKeys;

		if (!keys.isEmpty()) {
			Collection<@NonNull String> ks;
			if (keys.size() == 1 && "*".equals(keys.get(0))) {
				ks = m.keySet();
			} else {
				ks = keys;
			}
			buf.fg(Color.WHITE);
			buf.append(" ");
			buf.a(Attribute.INTENSITY_FAINT);
			buf.a("{");
			boolean first = true;
			for (String k : ks) {
				String v = m.get(k);
				if (v == null) {
					continue;
				}
				if (first) {
					first = false;
				} else {
					buf.append("&");
				}
				buf.append(URLEncoder.encode(k, StandardCharsets.US_ASCII));
				buf.append("=");
				buf.append(URLEncoder.encode(v, StandardCharsets.US_ASCII));

			}
			buf.a("}");
			buf.fg(Color.DEFAULT);
			buf.a(Attribute.RESET);
		}

		buf.fg(Color.DEFAULT);
		buf.a(" - ");

		buf.append(formattedMessage);

		write(output, buf.toString(), t);
	}

	public static String padRight(
			String s,
			int n) {
		return String.format("%-" + n + "s", s.substring(0, Math.min(s.length(), n)));
	}

	private Ansi colorLevel(
			Ansi ansi,
			Level level) {
		String levelStr = levelFormatter.format(level);
		switch (level) {
		case ERROR:
			ansi.a(Attribute.INTENSITY_BOLD).fg(Color.RED);
			break;
		case INFO:
			ansi.a(Attribute.INTENSITY_BOLD).fg(Color.BLUE);
			break;
		case WARNING:
			ansi.fg(Color.RED);
			break;
		case DEBUG:
			ansi.a(Attribute.INTENSITY_FAINT).fg(Color.CYAN);
		case TRACE:
		default:
			ansi.fg(Color.DEFAULT);
			break;
		}
		return ansi.a(levelStr).fg(Color.DEFAULT).a(Attribute.RESET).a("");
	}

	private String getFormattedDate(
			Instant instant) {
		String dateText = instantFormatter.format(instant);
		return dateText;
	}

	void write(
			LogOutput output,
			String buf,
			@Nullable Throwable t) {
		output.append(buf);
		output.append("\n");
		if (t != null) {
			throwableFormatter.format(output, t);
		}

	}

}

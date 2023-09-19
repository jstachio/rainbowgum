package io.jstach.rainbowgum.jansi;

import java.lang.System.Logger.Level;
import java.time.Instant;
import java.util.Map;

import org.eclipse.jdt.annotation.Nullable;
import org.fusesource.jansi.Ansi;
import org.fusesource.jansi.Ansi.Attribute;
import org.fusesource.jansi.Ansi.Color;
import org.fusesource.jansi.AnsiConsole;

import io.jstach.rainbowgum.LogEvent;
import io.jstach.rainbowgum.LogFormatter;

public class JansiLogFormatter implements LogFormatter.EventFormatter {

	private final LevelFormatter levelFormatter;

	private final InstantFormatter instantFormatter;

	private final NameFormatter nameFormatter;

	private final ThrowableFormatter throwableFormatter;

	private final KeyValuesFormatter keyValuesFormatter;

	private final ThreadFormatter threadFormatter;

	public JansiLogFormatter(LevelFormatter levelFormatter, InstantFormatter instantFormatter,
			NameFormatter nameFormatter, ThrowableFormatter throwableFormatter, KeyValuesFormatter keyValuesFormatter,
			ThreadFormatter threadFormatter) {
		super();
		this.levelFormatter = levelFormatter;
		this.instantFormatter = instantFormatter;
		this.nameFormatter = nameFormatter;
		this.throwableFormatter = throwableFormatter;
		this.keyValuesFormatter = keyValuesFormatter;
		this.threadFormatter = threadFormatter;
	}

	public static Builder builder() {
		return new Builder();
	}

	public static class Builder {

		private LevelFormatter levelFormatter = LevelFormatter.of();

		private InstantFormatter instantFormatter = InstantFormatter.of();

		private NameFormatter nameFormatter = NameFormatter.of();

		private ThrowableFormatter throwableFormatter = ThrowableFormatter.of();

		private KeyValuesFormatter keyValuesFormatter = LogFormatter.noop();

		private ThreadFormatter threadFormatter = ThreadFormatter.of();

		private Builder() {
		}

		public Builder levelFormatter(LevelFormatter levelFormatter) {
			this.levelFormatter = levelFormatter;
			return this;
		}

		public Builder instantFormatter(InstantFormatter instantFormatter) {
			this.instantFormatter = instantFormatter;
			return this;

		}

		public Builder nameFormatter(NameFormatter nameFormatter) {
			this.nameFormatter = nameFormatter;
			return this;

		}

		public Builder throwableFormatter(ThrowableFormatter throwableFormatter) {
			this.throwableFormatter = throwableFormatter;
			return this;

		}

		public Builder keyValuesFormatter(KeyValuesFormatter keyValuesFormatter) {
			this.keyValuesFormatter = keyValuesFormatter;
			return this;

		}

		public Builder threadFormatter(ThreadFormatter threadFormatter) {
			this.threadFormatter = threadFormatter;
			return this;
		}

		public JansiLogFormatter build() {
			if (installJansi()) {
				AnsiConsole.systemInstall();
			}
			return new JansiLogFormatter(levelFormatter, instantFormatter, nameFormatter, throwableFormatter,
					keyValuesFormatter, threadFormatter);
		}

		private static boolean installJansi() {
			if (!System.getProperty("surefire.real.class.path", "").isEmpty()) {
				return false;
			}
			return true;
		}

	}

	public void format(StringBuilder output, LogEvent logEvent) {

		var level = logEvent.level();
		var instant = logEvent.timeStamp();
		var name = logEvent.loggerName();
		@Nullable
		Throwable t = logEvent.throwable();
		var formattedMessage = logEvent.formattedMessage();

		// StringBuilder buf = new StringBuilder(32);
		Ansi buf = Ansi.ansi(output);

		// Append date-time if so configured

		buf.fg(Color.CYAN);
		buf.a(getFormattedDate(instant));
		buf.fg(Color.DEFAULT);
		buf.a(' ');

		// Append current thread name if so configured
		if (!threadFormatter.isNoop()) {
			buf.a(Attribute.INTENSITY_FAINT);
			buf.a("[");
			buf.append(threadFormatter.formatThread(logEvent.threadName()));
			buf.append("]");
			buf.a(Attribute.RESET);
			buf.a(" ");
		}

		colorLevel(buf, level);

		buf.append(' ');

		// Append the name of the log instance if so configured

		if (!nameFormatter.isNoop()) {
			buf.fg(Color.MAGENTA);
			buf.a(String.valueOf(name));
		}

		Map<String, @Nullable String> m = logEvent.keyValues();

		if (!LogFormatter.isNoop(keyValuesFormatter)) {
			buf.fg(Color.WHITE);
			buf.append(" ");
			buf.a(Attribute.INTENSITY_FAINT);
			buf.a("{");
			keyValuesFormatter.format(output, m);
			buf.a("}");
			buf.fg(Color.DEFAULT);
			buf.a(Attribute.RESET);
		}

		buf.fg(Color.DEFAULT);
		buf.a(" - ");

		buf.append(formattedMessage);
		output.append("\n");
		if (t != null) {
			throwableFormatter.format(output, t);
		}
	}

	private Ansi colorLevel(Ansi ansi, Level level) {
		if (levelFormatter.isNoop())
			return ansi;
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

	private String getFormattedDate(Instant instant) {
		String dateText = instantFormatter.format(instant);
		return dateText;
	}

}

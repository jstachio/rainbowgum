package io.jstach.rainbowgum.jansi;

import java.lang.System.Logger.Level;
import java.time.Instant;

import org.eclipse.jdt.annotation.Nullable;
import org.fusesource.jansi.Ansi;
import org.fusesource.jansi.Ansi.Attribute;
import org.fusesource.jansi.Ansi.Color;

import io.jstach.rainbowgum.KeyValues;
import io.jstach.rainbowgum.LogEvent;
import io.jstach.rainbowgum.LogFormatter;
import io.jstach.rainbowgum.format.AbstractStandardEventFormatter;
import io.jstach.rainbowgum.format.StandardEventFormatter;

public class JansiLogFormatter extends AbstractStandardEventFormatter {

	protected JansiLogFormatter( //
			LevelFormatter levelFormatter, //
			InstantFormatter instantFormatter, //
			NameFormatter nameFormatter, //
			MessageFormatter messageFormatter, //
			ThrowableFormatter throwableFormatter, //
			KeyValuesFormatter keyValuesFormatter, //
			ThreadFormatter threadFormatter) {
		super(levelFormatter, instantFormatter, nameFormatter, messageFormatter, throwableFormatter, keyValuesFormatter,
				threadFormatter);
	}

	public static Builder builder() {
		return new Builder();
	}

	public static class Builder extends StandardEventFormatter.AbstractBuilder<Builder> {

		private Builder() {
		}

		public JansiLogFormatter build() {

			return new JansiLogFormatter(levelFormatter, instantFormatter, nameFormatter, messageFormatter,
					throwableFormatter, keyValuesFormatter, threadFormatter);
		}

		@Override
		protected Builder self() {
			return this;
		}

	}

	public void format(StringBuilder output, LogEvent logEvent) {

		var level = logEvent.level();
		var instant = logEvent.timeStamp();
		var name = logEvent.loggerName();
		@Nullable
		Throwable t = logEvent.throwable();

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
			threadFormatter.format(output, logEvent);
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

		KeyValues m = logEvent.keyValues();

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
		messageFormatter.formatMessage(output, logEvent);
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

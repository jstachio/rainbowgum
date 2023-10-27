package io.jstach.rainbowgum.jansi;

import java.lang.System.Logger.Level;

import org.fusesource.jansi.Ansi;
import org.fusesource.jansi.Ansi.Attribute;
import org.fusesource.jansi.Ansi.Color;

import io.jstach.rainbowgum.LogEvent;
import io.jstach.rainbowgum.LogFormatter;
import io.jstach.rainbowgum.format.AbstractStandardEventFormatter;
import io.jstach.rainbowgum.format.StandardEventFormatter;

public class JansiLogFormatter extends AbstractStandardEventFormatter {

	protected JansiLogFormatter( //
			LevelFormatter levelFormatter, //
			TimestampFormatter timestampFormatter, //
			NameFormatter nameFormatter, //
			MessageFormatter messageFormatter, //
			ThrowableFormatter throwableFormatter, //
			KeyValuesFormatter keyValuesFormatter, //
			ThreadFormatter threadFormatter) {
		super(timestampFormatter, threadFormatter, levelFormatter, nameFormatter, messageFormatter, throwableFormatter,
				keyValuesFormatter);
	}

	public static Builder builder() {
		return new Builder();
	}

	public static class Builder extends StandardEventFormatter.AbstractBuilder<Builder> {

		private Builder() {
		}

		public JansiLogFormatter build() {

			return new JansiLogFormatter(levelFormatter, timestampFormatter, nameFormatter, messageFormatter,
					throwableFormatter, keyValuesFormatter, threadFormatter);
		}

		@Override
		protected Builder self() {
			return this;
		}

	}

	public void format(StringBuilder output, LogEvent logEvent) {

		var level = logEvent.level();
		var name = logEvent.loggerName();

		// StringBuilder buf = new StringBuilder(32);
		Ansi buf = Ansi.ansi(output);

		// Append date-time if so configured

		buf.fg(Color.CYAN);
		timestampFormatter.format(output, logEvent);
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

		colorLevel(buf, output, level);

		buf.append(' ');

		// Append the name of the log instance if so configured

		if (!nameFormatter.isNoop()) {
			buf.fg(Color.MAGENTA);
			buf.a(String.valueOf(name));
		}

		if (!LogFormatter.isNoop(keyValuesFormatter)) {
			buf.fg(Color.WHITE);
			buf.append(" ");
			buf.a(Attribute.INTENSITY_FAINT);
			buf.a("{");
			keyValuesFormatter.format(output, logEvent);
			buf.a("}");
			buf.fg(Color.DEFAULT);
			buf.a(Attribute.RESET);
		}

		buf.fg(Color.DEFAULT);
		buf.a(" - ");
		messageFormatter.format(output, logEvent);
		output.append("\n");
		throwableFormatter.format(output, logEvent);
	}

	private Ansi colorLevel(Ansi ansi, StringBuilder sb, Level level) {
		if (levelFormatter.isNoop())
			return ansi;
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
		ansi.a("");
		levelFormatter.formatLevel(sb, level);
		return ansi.a("").fg(Color.DEFAULT).a(Attribute.RESET).a("");
	}

}

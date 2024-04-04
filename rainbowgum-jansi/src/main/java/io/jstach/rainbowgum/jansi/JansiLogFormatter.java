package io.jstach.rainbowgum.jansi;

import org.fusesource.jansi.Ansi;
import org.fusesource.jansi.Ansi.Attribute;
import org.fusesource.jansi.Ansi.Color;

import io.jstach.rainbowgum.LogEvent;
import io.jstach.rainbowgum.LogFormatter;
import io.jstach.rainbowgum.format.AbstractStandardEventFormatter;
import io.jstach.rainbowgum.format.StandardEventFormatter;

/**
 * Jansi TTLL formater.
 */
public final class JansiLogFormatter extends AbstractStandardEventFormatter {

	JansiLogFormatter( //
			LogFormatter timestampFormatter, //
			LogFormatter threadFormatter, //
			LogFormatter levelFormatter, //
			LogFormatter nameFormatter, //
			LogFormatter messageFormatter, //
			LogFormatter throwableFormatter, //
			LogFormatter keyValuesFormatter) {
		super(timestampFormatter, threadFormatter, levelFormatter, nameFormatter, messageFormatter, throwableFormatter,
				keyValuesFormatter);
	}

	/**
	 * Builder.
	 * @return builder.
	 */
	public static Builder builder() {
		return new Builder();
	}

	/**
	 * Jansi log formatter builder.
	 */
	public static class Builder extends StandardEventFormatter.AbstractBuilder<Builder> {

		private Builder() {
			levelFormatter = LevelFormatter.ofRightPadded();
		}

		/**
		 * Builds.
		 * @return formatter.
		 */
		public JansiLogFormatter build() {

			return new JansiLogFormatter(timestampFormatter, threadFormatter, levelFormatter, nameFormatter,
					messageFormatter, throwableFormatter, keyValuesFormatter);
		}

		@Override
		protected Builder self() {
			return this;
		}

	}

	public void format(StringBuilder output, LogEvent logEvent) {

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

		colorLevel(buf, output, logEvent);

		buf.append(' ');

		// Append the name of the log instance if so configured

		if (!nameFormatter.isNoop()) {
			buf.fg(Color.MAGENTA);
			buf.a(String.valueOf(name));
		}

		if (!LogFormatter.isNoopOrNull(keyValuesFormatter)) {
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

	private Ansi colorLevel(Ansi ansi, StringBuilder sb, LogEvent event) {
		var level = event.level();
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
		levelFormatter.format(sb, event);
		return ansi.a("").fg(Color.DEFAULT).a(Attribute.RESET).a("");
	}

}

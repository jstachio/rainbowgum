package io.jstach.rainbowgum.jansi;

import org.fusesource.jansi.Ansi.Attribute;
import org.fusesource.jansi.Ansi.Color;

import io.jstach.rainbowgum.LogEvent;
import io.jstach.rainbowgum.LogFormatter;
import io.jstach.rainbowgum.format.AbstractStandardEventFormatter;

// TODO deprecate
/**
 * Jansi TTLL formater.
 */
public final class JansiLogFormatter extends AbstractStandardEventFormatter {

	private final AnsiHelper ansiHelper;

	JansiLogFormatter( //
			boolean ansi, LogFormatter timestampFormatter, //
			LogFormatter threadFormatter, //
			LogFormatter levelFormatter, //
			LogFormatter nameFormatter, //
			LogFormatter messageFormatter, //
			LogFormatter throwableFormatter, //
			LogFormatter keyValuesFormatter) {
		super(timestampFormatter, threadFormatter, levelFormatter, nameFormatter, messageFormatter, throwableFormatter,
				keyValuesFormatter);
		this.ansiHelper = ansi ? AnsiHelper.ANSI : AnsiHelper.NO_ANSI;
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
	public static class Builder extends AbstractStandardEventFormatter.AbstractBuilder<Builder> {

		private boolean disableAnsi = false;

		private Builder() {
			levelFormatter = LevelFormatter.ofRightPadded();
		}

		/**
		 * Builds.
		 * @return formatter.
		 */
		public JansiLogFormatter build() {

			return new JansiLogFormatter(!disableAnsi, timestampFormatter, threadFormatter, levelFormatter,
					nameFormatter, messageFormatter, throwableFormatter, keyValuesFormatter);
		}

		/**
		 * If true will disable ansi escape characters regardless of JAnsi.
		 * @param disableAnsi true will not ommit ansi escape chars.
		 * @return this.
		 */
		public Builder disableAnsi(boolean disableAnsi) {
			this.disableAnsi = disableAnsi;
			return this;
		}

		@Override
		protected Builder self() {
			return this;
		}

	}

	@Override
	public void format(StringBuilder output, LogEvent logEvent) {

		var sb = output;

		ansiHelper.fg(sb, Color.CYAN);
		timestampFormatter.format(output, logEvent);
		ansiHelper.fg(sb, Color.DEFAULT);
		sb.append(" ");

		// Append current thread name if so configured
		if (!threadFormatter.isNoop()) {
			ansiHelper.a(sb, Attribute.INTENSITY_FAINT);
			sb.append("[");
			threadFormatter.format(output, logEvent);
			sb.append("]");
			ansiHelper.a(sb, Attribute.RESET);
			sb.append(" ");
		}

		colorLevel(output, logEvent);

		sb.append(' ');

		// Append the name of the log instance if so configured

		if (!nameFormatter.isNoop()) {
			ansiHelper.fg(sb, Color.MAGENTA);
			nameFormatter.format(output, logEvent);
		}

		if (!LogFormatter.isNoopOrNull(keyValuesFormatter)) {
			sb.append(" ");
			ansiHelper.fg(sb, Color.WHITE, Attribute.INTENSITY_FAINT);
			sb.append("{");
			keyValuesFormatter.format(output, logEvent);
			sb.append("}");
		}
		ansiHelper.fg(sb, Color.DEFAULT);
		sb.append(" - ");
		messageFormatter.format(output, logEvent);
		output.append("\n");
		throwableFormatter.format(output, logEvent);
	}

	private void colorLevel(StringBuilder sb, LogEvent event) {
		var level = event.level();
		if (levelFormatter.isNoop())
			return;
		switch (level) {
			case ERROR:
				ansiHelper.fg(sb, Attribute.INTENSITY_BOLD, Color.RED);
				break;
			case INFO:
				ansiHelper.fg(sb, Attribute.INTENSITY_BOLD, Color.BLUE);
				break;
			case WARNING:
				ansiHelper.fg(sb, Color.RED);
				break;
			case DEBUG:
			case TRACE:
			default:
				ansiHelper.fg(sb, Color.DEFAULT);
		}
		levelFormatter.format(sb, event);
		ansiHelper.fg(sb, Color.DEFAULT, Attribute.RESET);
	}

	private static final char FIRST_ESC_CHAR = 27;

	private static final char SECOND_ESC_CHAR = '[';

	enum AnsiHelper {

		ANSI {
			@Override
			void fg(StringBuilder sb, Color color) {
				sb.append(FIRST_ESC_CHAR);
				sb.append(SECOND_ESC_CHAR);
				sb.append(color.fg());
				sb.append('m');
			}

			@Override
			void fg(StringBuilder sb, Color color, Attribute attribute) {
				sb.append(FIRST_ESC_CHAR);
				sb.append(SECOND_ESC_CHAR);
				sb.append(color.fg());
				sb.append(";");
				sb.append(attribute.value());
				sb.append('m');
			}

			@Override
			void fg(StringBuilder sb, Attribute attribute, Color color) {
				sb.append(FIRST_ESC_CHAR);
				sb.append(SECOND_ESC_CHAR);
				sb.append(attribute.value());
				sb.append(";");
				sb.append(color.fg());
				sb.append('m');
			}

			@Override
			void a(StringBuilder sb, Attribute attribute) {
				sb.append(FIRST_ESC_CHAR);
				sb.append(SECOND_ESC_CHAR);
				if (attribute.value() != 0) {
					sb.append(attribute.value());
				}
				sb.append('m');
			}
		},
		NO_ANSI {
			@Override
			void fg(StringBuilder sb, Color color) {
			}

			@Override
			void fg(StringBuilder sb, Color color, Attribute attribute) {
			}

			@Override
			void fg(StringBuilder sb, Attribute attribute, Color color) {
			}

			@Override
			void a(StringBuilder sb, Attribute attribute) {
			}
		};

		abstract void fg(StringBuilder sb, Color color);

		abstract void fg(StringBuilder sb, Color color, Attribute attribute);

		abstract void fg(StringBuilder sb, Attribute attribute, Color color);

		abstract void a(StringBuilder sb, Attribute attribute);

	}

}

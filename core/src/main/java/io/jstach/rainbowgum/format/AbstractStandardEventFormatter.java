package io.jstach.rainbowgum.format;

import java.time.Instant;

import org.eclipse.jdt.annotation.Nullable;

import io.jstach.rainbowgum.KeyValues;
import io.jstach.rainbowgum.LogEvent;
import io.jstach.rainbowgum.LogFormatter;

public class AbstractStandardEventFormatter implements LogFormatter.EventFormatter {

	protected final LevelFormatter levelFormatter;

	protected final InstantFormatter instantFormatter;

	protected final NameFormatter nameFormatter;

	protected final ThrowableFormatter throwableFormatter;

	protected final KeyValuesFormatter keyValuesFormatter;

	protected final ThreadFormatter threadFormatter;

	protected AbstractStandardEventFormatter(LevelFormatter levelFormatter, InstantFormatter instantFormatter,
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

	public static abstract class AbstractBuilder<T> {

		protected LevelFormatter levelFormatter = LevelFormatter.of();

		protected InstantFormatter instantFormatter = InstantFormatter.of();

		protected NameFormatter nameFormatter = NameFormatter.of();

		protected ThrowableFormatter throwableFormatter = ThrowableFormatter.of();

		protected KeyValuesFormatter keyValuesFormatter = LogFormatter.noop();

		protected ThreadFormatter threadFormatter = ThreadFormatter.of();

		protected abstract T self();

		public T levelFormatter(LevelFormatter levelFormatter) {
			this.levelFormatter = levelFormatter;
			return self();
		}

		public T instantFormatter(InstantFormatter instantFormatter) {
			this.instantFormatter = instantFormatter;
			return self();

		}

		public T nameFormatter(NameFormatter nameFormatter) {
			this.nameFormatter = nameFormatter;
			return self();

		}

		public T throwableFormatter(ThrowableFormatter throwableFormatter) {
			this.throwableFormatter = throwableFormatter;
			return self();

		}

		public T keyValuesFormatter(KeyValuesFormatter keyValuesFormatter) {
			this.keyValuesFormatter = keyValuesFormatter;
			return self();

		}

		public T threadFormatter(ThreadFormatter threadFormatter) {
			this.threadFormatter = threadFormatter;
			return self();
		}

	}

	public void format(StringBuilder output, LogEvent logEvent) {

		var level = logEvent.level();
		var instant = logEvent.timeStamp();
		var name = logEvent.loggerName();
		@Nullable
		Throwable t = logEvent.throwable();
		var formattedMessage = logEvent.formattedMessage();

		// Append date-time if so configured

		output.append(getFormattedDate(instant));
		output.append(' ');

		// Append current thread name if so configured
		if (!threadFormatter.isNoop()) {
			output.append("[");
			output.append(threadFormatter.formatThread(logEvent.threadName()));
			output.append("]");
			output.append(" ");
		}

		if (!levelFormatter.isNoop()) {
			output.append(levelFormatter.format(level));
			output.append(' ');
		}

		// Append the name of the log instance if so configured

		output.append(name);

		KeyValues m = logEvent.keyValues();

		if (!LogFormatter.isNoop(keyValuesFormatter)) {
			output.append(" ");
			output.append("{");
			keyValuesFormatter.format(output, m);
			output.append("}");
		}

		output.append(" - ");

		output.append(formattedMessage);
		output.append("\n");

		if (t != null) {
			throwableFormatter.format(output, t);
		}
	}

	private String getFormattedDate(Instant instant) {
		String dateText = instantFormatter.format(instant);
		return dateText;
	}

}

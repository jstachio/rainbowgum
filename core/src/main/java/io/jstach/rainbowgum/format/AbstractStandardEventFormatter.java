package io.jstach.rainbowgum.format;

import org.eclipse.jdt.annotation.Nullable;

import io.jstach.rainbowgum.LogEvent;
import io.jstach.rainbowgum.LogFormatter;

/**
 * An abstract formatter that has the semi standard layout of <strong>TTLL - Time, Thread,
 * Level, Logger</strong> but allows changing the format of time, thread, level, logger,
 * etc. The formatters get called in the following order.
 * <ol>
 * <li>{@link io.jstach.rainbowgum.LogFormatter.TimestampFormatter}</li>
 * <li>{@link io.jstach.rainbowgum.LogFormatter.ThreadFormatter}</li>
 * <li>{@link io.jstach.rainbowgum.LogFormatter.LevelFormatter}</li>
 * <li>{@link io.jstach.rainbowgum.LogFormatter.NameFormatter}</li>
 * <li>{@link io.jstach.rainbowgum.LogFormatter.KeyValuesFormatter}</li>
 * <li>{@link io.jstach.rainbowgum.LogFormatter.MessageFormatter}</li>
 * <li>{@link io.jstach.rainbowgum.LogFormatter.ThrowableFormatter}</li>
 * </ol>
 */
public class AbstractStandardEventFormatter implements LogFormatter.EventFormatter {

	protected final TimestampFormatter timestampFormatter;

	protected final ThreadFormatter threadFormatter;

	protected final LevelFormatter levelFormatter;

	protected final NameFormatter nameFormatter;

	protected final MessageFormatter messageFormatter;

	protected final KeyValuesFormatter keyValuesFormatter;

	protected final ThrowableFormatter throwableFormatter;

	protected AbstractStandardEventFormatter( //
			TimestampFormatter timestampFormatter, //
			ThreadFormatter threadFormatter, //
			LevelFormatter levelFormatter, //
			NameFormatter nameFormatter, //
			MessageFormatter messageFormatter, //
			ThrowableFormatter throwableFormatter, //
			KeyValuesFormatter keyValuesFormatter) {
		super();
		this.levelFormatter = levelFormatter;
		this.timestampFormatter = timestampFormatter;
		this.nameFormatter = nameFormatter;
		this.messageFormatter = messageFormatter;
		this.throwableFormatter = throwableFormatter;
		this.keyValuesFormatter = keyValuesFormatter;
		this.threadFormatter = threadFormatter;
	}

	/**
	 * Abstract TTLL Builder.
	 *
	 * @param <T> builder type.
	 */
	public static abstract class AbstractBuilder<T> {

		protected LevelFormatter levelFormatter = LevelFormatter.of();

		protected TimestampFormatter timestampFormatter = TimestampFormatter.of();

		protected NameFormatter nameFormatter = NameFormatter.of();

		protected MessageFormatter messageFormatter = MessageFormatter.of();

		protected ThrowableFormatter throwableFormatter = ThrowableFormatter.of();

		protected KeyValuesFormatter keyValuesFormatter = LogFormatter.noop();

		protected ThreadFormatter threadFormatter = ThreadFormatter.of();

		protected abstract T self();

		/**
		 * Do nothing constructor
		 */
		protected AbstractBuilder() {
		}

		/**
		 * Sets timestamp formatter. If not set
		 * {@link io.jstach.rainbowgum.LogFormatter.TimestampFormatter#of()} will be used.
		 * @param timestampFormatter formatter to use for rendering
		 * {@link LogEvent#timestamp()}.
		 * @return this builder.
		 */
		public T timestampFormatter(TimestampFormatter timestampFormatter) {
			this.timestampFormatter = timestampFormatter;
			return self();

		}

		/**
		 * Sets thread formatter. If not set
		 * {@link io.jstach.rainbowgum.LogFormatter.ThreadFormatter#of()} will be used.
		 * @param threadFormatter formatter to use for rendering thread information.
		 * @return this builder.
		 */
		public T threadFormatter(ThreadFormatter threadFormatter) {
			this.threadFormatter = threadFormatter;
			return self();
		}

		/**
		 * Sets the level formatter. If not set
		 * {@link io.jstach.rainbowgum.LogFormatter.LevelFormatter#of()} will be used.
		 * @param levelFormatter formatter to use for rendering level information.
		 * @return this builder.
		 */
		public T levelFormatter(LevelFormatter levelFormatter) {
			this.levelFormatter = levelFormatter;
			return self();
		}

		/**
		 * Sets the logger name formatter. If not set
		 * {@link io.jstach.rainbowgum.LogFormatter.NameFormatter#of()} will be used.
		 * @param nameFormatter formatter to use for rendering logger name.
		 * @return this builder.
		 */
		public T nameFormatter(NameFormatter nameFormatter) {
			this.nameFormatter = nameFormatter;
			return self();

		}

		/**
		 * Sets the throwable formatter. If not set
		 * {@link io.jstach.rainbowgum.LogFormatter.ThrowableFormatter#of()} will be used.
		 * @param throwableFormatter formatter to use for rendering throwables.
		 * @return this builder.
		 */
		public T throwableFormatter(ThrowableFormatter throwableFormatter) {
			this.throwableFormatter = throwableFormatter;
			return self();

		}

		/**
		 * Sets the key values formatter. If not set
		 * {@link io.jstach.rainbowgum.LogFormatter.KeyValuesFormatter#of()} will be used.
		 * @param keyValuesFormatter formatter to use for rendering key values.
		 * @return this builder.
		 */
		public T keyValuesFormatter(KeyValuesFormatter keyValuesFormatter) {
			this.keyValuesFormatter = keyValuesFormatter;
			return self();
		}

	}

	public void format(StringBuilder output, LogEvent logEvent) {

		var name = logEvent.loggerName();

		@Nullable
		Throwable t = logEvent.throwable();

		// Append date-time if so configured

		timestampFormatter.format(output, logEvent);

		output.append(' ');

		// Append current thread name if so configured
		if (!threadFormatter.isNoop()) {
			output.append("[");
			threadFormatter.format(output, logEvent);
			output.append("]");
			output.append(" ");
		}

		if (!levelFormatter.isNoop()) {
			levelFormatter.format(output, logEvent);
			output.append(' ');
		}

		// Append the name of the log instance if so configured

		output.append(name);

		if (!LogFormatter.isNoop(keyValuesFormatter)) {
			output.append(" ");
			output.append("{");
			keyValuesFormatter.format(output, logEvent);
			output.append("}");
		}

		output.append(" - ");
		messageFormatter.format(output, logEvent);
		output.append("\n");

		if (t != null) {
			throwableFormatter.format(output, logEvent);
		}
	}

}

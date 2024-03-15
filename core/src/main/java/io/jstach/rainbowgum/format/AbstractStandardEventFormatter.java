package io.jstach.rainbowgum.format;

import io.jstach.rainbowgum.LogEvent;
import io.jstach.rainbowgum.LogFormatter;

/**
 * An abstract formatter that has the semi-standard layout of <strong>TTLL - Time, Thread,
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

	/**
	 * immutable field
	 */
	protected final TimestampFormatter timestampFormatter;

	/**
	 * immutable field
	 */
	protected final ThreadFormatter threadFormatter;

	/**
	 * immutable field
	 */
	protected final LevelFormatter levelFormatter;

	/**
	 * immutable field
	 */
	protected final NameFormatter nameFormatter;

	/**
	 * immutable field
	 */
	protected final MessageFormatter messageFormatter;

	/**
	 * immutable field
	 */
	protected final KeyValuesFormatter keyValuesFormatter;

	/**
	 * immutable field
	 */
	protected final ThrowableFormatter throwableFormatter;

	/**
	 * Combined formatter.
	 */
	protected final LogFormatter eventFormatter;

	/**
	 * Override
	 * @param timestampFormatter not <code>null</code>.
	 * @param threadFormatter not <code>null</code>.
	 * @param levelFormatter not <code>null</code>.
	 * @param nameFormatter not <code>null</code>.
	 * @param messageFormatter not <code>null</code>.
	 * @param throwableFormatter not <code>null</code>.
	 * @param keyValuesFormatter not <code>null</code>.
	 */
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
		var b = LogFormatter.builder();
		if (!timestampFormatter.isNoop()) {
			b.add(timestampFormatter);
			b.text(" ");
		}
		if (!threadFormatter.isNoop()) {
			b.text("[");
			b.add(threadFormatter);
			b.text("] ");
		}
		if (!levelFormatter.isNoop()) {
			b.add(levelFormatter);
			b.text(" ");
		}
		b.add(LogFormatter.NameFormatter.of());
		if (!keyValuesFormatter.isNoop()) {
			b.text(" {");
			b.add(keyValuesFormatter);
			b.text("}");
		}
		if (!messageFormatter.isNoop()) {
			b.text(" - ");
			b.add(messageFormatter);
		}
		b.newline();
		if (!throwableFormatter.isNoop()) {
			b.add(throwableFormatter);
		}
		this.eventFormatter = b.flatten();

	}

	/**
	 * Abstract TTLL Builder.
	 *
	 * @param <T> builder type.
	 */
	public static abstract class AbstractBuilder<T> {

		/**
		 * mutable field
		 */
		protected LevelFormatter levelFormatter = LevelFormatter.of();

		/**
		 * mutable field
		 */
		protected TimestampFormatter timestampFormatter = TimestampFormatter.of();

		/**
		 * mutable field
		 */
		protected NameFormatter nameFormatter = NameFormatter.of();

		/**
		 * mutable field
		 */
		protected MessageFormatter messageFormatter = MessageFormatter.of();

		/**
		 * mutable field
		 */
		protected ThrowableFormatter throwableFormatter = ThrowableFormatter.of();

		/**
		 * mutable field
		 */
		protected KeyValuesFormatter keyValuesFormatter = LogFormatter.noop();

		/**
		 * mutable field
		 */
		protected ThreadFormatter threadFormatter = ThreadFormatter.of();

		/**
		 * For builder
		 * @return this.
		 */
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

	@Override
	public void format(StringBuilder output, LogEvent logEvent) {
		eventFormatter.format(output, logEvent);
		//
		// var name = logEvent.loggerName();
		//
		// @Nullable
		// Throwable t = logEvent.throwableOrNull();
		//
		// // Append date-time if so configured
		//
		// timestampFormatter.format(output, logEvent);
		//
		// if (!timestampFormatter.isNoop()) {
		// output.append(' ');
		// }
		//
		// // Append current thread name if so configured
		// if (!threadFormatter.isNoop()) {
		// output.append("[");
		// threadFormatter.format(output, logEvent);
		// output.append("]");
		// output.append(" ");
		// }
		//
		// if (!levelFormatter.isNoop()) {
		// levelFormatter.format(output, logEvent);
		// output.append(' ');
		// }
		//
		// // Append the name of the log instance if so configured
		//
		// output.append(name);
		//
		// if (!LogFormatter.isNoopOrNull(keyValuesFormatter)) {
		// output.append(" ");
		// output.append("{");
		// keyValuesFormatter.format(output, logEvent);
		// output.append("}");
		// }
		//
		// output.append(" - ");
		// messageFormatter.format(output, logEvent);
		// output.append("\n");
		//
		// if (t != null) {
		// throwableFormatter.format(output, logEvent);
		// }
	}

}

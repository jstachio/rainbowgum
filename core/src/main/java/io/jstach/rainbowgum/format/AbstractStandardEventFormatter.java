package io.jstach.rainbowgum.format;

import io.jstach.rainbowgum.LogEncoder;
import io.jstach.rainbowgum.LogEncoder.EncoderProvider;
import io.jstach.rainbowgum.LogEncoderRegistry;
import io.jstach.rainbowgum.LogEvent;
import io.jstach.rainbowgum.LogFormatter;

/**
 * An abstract formatter where the actual full layout of the event is not a concern of the
 * user and by default usually has the semi-standard layout of <strong>TTLL - Time,
 * Thread, Level, Logger</strong>.
 *
 * It allows changing the format of time, thread, level, logger, etc. Typically the
 * formatters get called in the following order.
 * <ol>
 * <li>Timestamp</li>
 * <li>Thread</li>
 * <li>Level</li>
 * <li>Logger Name</li>
 * <li>Key Values if enabled</li>
 * <li>Formatted Message</li>
 * <li>Throwable if not null</li>
 * </ol>
 * If plugins would like to provide some sort of standard formatter they should extend
 * this class as well as the {@link AbstractBuilder}.
 *
 * @see AbstractBuilder
 */
public class AbstractStandardEventFormatter implements LogFormatter.EventFormatter {

	/**
	 * Recommended URI schema for this type of encoder.
	 * @see LogEncoderRegistry#register(String,
	 * io.jstach.rainbowgum.LogEncoder.EncoderProvider)
	 */
	public static final String SCHEMA = "ttll";

	/**
	 * immutable field
	 */
	protected final LogFormatter timestampFormatter;

	/**
	 * immutable field
	 */
	protected final LogFormatter threadFormatter;

	/**
	 * immutable field
	 */
	protected final LogFormatter levelFormatter;

	/**
	 * immutable field
	 */
	protected final LogFormatter nameFormatter;

	/**
	 * immutable field
	 */
	protected final LogFormatter messageFormatter;

	/**
	 * immutable field
	 */
	protected final LogFormatter keyValuesFormatter;

	/**
	 * immutable field
	 */
	protected final LogFormatter throwableFormatter;

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
			LogFormatter timestampFormatter, //
			LogFormatter threadFormatter, //
			LogFormatter levelFormatter, //
			LogFormatter nameFormatter, //
			LogFormatter messageFormatter, //
			LogFormatter throwableFormatter, //
			LogFormatter keyValuesFormatter) {
		super();
		this.levelFormatter = levelFormatter;
		this.threadFormatter = threadFormatter;
		this.timestampFormatter = timestampFormatter;
		this.nameFormatter = nameFormatter;
		this.messageFormatter = messageFormatter;
		this.throwableFormatter = throwableFormatter;
		this.keyValuesFormatter = keyValuesFormatter;

		this.eventFormatter = build( //
				timestampFormatter, //
				threadFormatter, //
				levelFormatter, //
				nameFormatter, //
				messageFormatter, //
				throwableFormatter, //
				keyValuesFormatter);

	}

	/**
	 * Creates the standard default TTLL layout. This is largely the default of Log4J2 and
	 * Logback if no formatting is picked. An example of what the default roughly looks
	 * like:
	 *
	 * <pre>
	 * 17:47:33.163 [main] WARN  com.my.MyLogger - Hello Warn Message {k1=v1}
	 * 17:47:33.167 [main] DEBUG com.my.MyLogger - Hello Debug Message {k1=v1}
	 *
	 * </pre>
	 * @param timestampFormatter not <code>null</code>.
	 * @param threadFormatter not <code>null</code>.
	 * @param levelFormatter not <code>null</code>.
	 * @param nameFormatter not <code>null</code>.
	 * @param messageFormatter not <code>null</code>.
	 * @param throwableFormatter not <code>null</code>.
	 * @param keyValuesFormatter not <code>null</code>.
	 * @return formatter.
	 */
	public static LogFormatter build(LogFormatter timestampFormatter, //
			LogFormatter threadFormatter, //
			LogFormatter levelFormatter, //
			LogFormatter nameFormatter, //
			LogFormatter messageFormatter, //
			LogFormatter throwableFormatter, //
			LogFormatter keyValuesFormatter) {
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
		b.add(nameFormatter);
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
		return b.build();
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
		protected LogFormatter levelFormatter = LevelFormatter.ofRightPadded();

		/**
		 * mutable field
		 */
		protected LogFormatter timestampFormatter = TimestampFormatter.of();

		/**
		 * mutable field
		 */
		protected LogFormatter threadFormatter = LogFormatter.builder().threadName().build();

		/**
		 * mutable field
		 */
		protected LogFormatter nameFormatter = LogFormatter.builder().loggerName().build();

		/**
		 * mutable field
		 */
		protected LogFormatter messageFormatter = LogFormatter.builder().message().build();

		/**
		 * mutable field
		 */
		protected LogFormatter throwableFormatter = ThrowableFormatter.of();

		/**
		 * mutable field
		 */
		protected LogFormatter keyValuesFormatter = LogFormatter.noop();

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
		public T timestampFormatter(LogFormatter timestampFormatter) {
			this.timestampFormatter = timestampFormatter;
			return self();

		}

		/**
		 * Sets thread formatter. If not set thread name will be used.
		 * @param threadFormatter formatter to use for rendering thread information.
		 * @return this builder.
		 */
		public T threadFormatter(LogFormatter threadFormatter) {
			this.threadFormatter = threadFormatter;
			return self();
		}

		/**
		 * Sets the level formatter. If not set
		 * {@link io.jstach.rainbowgum.LogFormatter.LevelFormatter#ofRightPadded()} will
		 * be used.
		 * @param levelFormatter formatter to use for rendering level information.
		 * @return this builder.
		 */
		public T levelFormatter(LogFormatter levelFormatter) {
			this.levelFormatter = levelFormatter;
			return self();
		}

		/**
		 * Sets the logger name formatter. If not set the default will be used.
		 * @param nameFormatter formatter to use for rendering logger name.
		 * @return this builder.
		 */
		public T nameFormatter(LogFormatter nameFormatter) {
			this.nameFormatter = nameFormatter;
			return self();

		}

		/**
		 * Sets the throwable formatter. If not set
		 * {@link io.jstach.rainbowgum.LogFormatter.ThrowableFormatter#of()} will be used.
		 * @param throwableFormatter formatter to use for rendering throwables.
		 * @return this builder.
		 */
		public T throwableFormatter(LogFormatter throwableFormatter) {
			this.throwableFormatter = throwableFormatter;
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
		 * Sets the key values formatter. If not set the default key values formatter will
		 * be used which is a noop.
		 * @param keyValuesFormatter formatter to use for rendering key values.
		 * @return this builder.
		 */
		public T keyValuesFormatter(LogFormatter keyValuesFormatter) {
			this.keyValuesFormatter = keyValuesFormatter;
			return self();
		}

	}

	/**
	 * Convenience to register the encoder.
	 * @return encoder provider.
	 * @see AbstractStandardEventFormatter#SCHEMA
	 */
	public EncoderProvider toEncoderProvider() {
		return EncoderProvider.of(LogEncoder.of(eventFormatter));
	}

	/**
	 * Will register this TTLL like encoder with the uri schema {@value #SCHEMA} and will
	 * be used as the default encoder if other encoders are not found.
	 * @param registry encoder registry.
	 */
	public void register(LogEncoderRegistry registry) {
		registry.register(SCHEMA, toEncoderProvider());
	}

	@Override
	public void format(StringBuilder output, LogEvent logEvent) {
		eventFormatter.format(output, logEvent);
	}

}

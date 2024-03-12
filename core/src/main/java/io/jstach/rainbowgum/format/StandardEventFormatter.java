package io.jstach.rainbowgum.format;

/**
 * A <strong>TTLL</strong> formatter that has the layout of <strong>TTLL - Time, Thread,
 * Level, Logger</strong> but allows changing the format of time, thread, level, logger,
 * etc.
 */
public final class StandardEventFormatter extends AbstractStandardEventFormatter {

	private StandardEventFormatter( //
			LevelFormatter levelFormatter, //
			TimestampFormatter timestampFormatter, //
			NameFormatter nameFormatter, //
			MessageFormatter messageFormatter, //
			ThrowableFormatter throwableFormatter, //
			KeyValuesFormatter keyValuesFormatter, ThreadFormatter threadFormatter) {
		super(timestampFormatter, threadFormatter, levelFormatter, nameFormatter, messageFormatter, throwableFormatter,
				keyValuesFormatter);
	}

	/**
	 * Create {@link StandardEventFormatter} builder.
	 * @return builder.
	 */
	public static Builder builder() {
		return new Builder();
	}

	/**
	 * Builder for {@link StandardEventFormatter} aka TTLL formatter.
	 */
	public static final class Builder extends AbstractBuilder<Builder> {

		private Builder() {
			super();
		}

		@Override
		protected Builder self() {
			return this;
		}

		/**
		 * Builds the formatter.
		 * @return formatter.
		 */
		public StandardEventFormatter build() {
			return new StandardEventFormatter(levelFormatter, timestampFormatter, nameFormatter, messageFormatter,
					throwableFormatter, keyValuesFormatter, threadFormatter);
		}

	}

}

package io.jstach.rainbowgum.format;

import io.jstach.rainbowgum.LogFormatter;

/**
 * A <strong>TTLL</strong> formatter that has the layout of <strong>TTLL - Time, Thread,
 * Level, Logger</strong> but allows changing the format of time, thread, level, logger,
 * etc.
 */
public final class StandardEventFormatter extends AbstractStandardEventFormatter {

	StandardEventFormatter( //
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
			return new StandardEventFormatter(timestampFormatter, threadFormatter, levelFormatter, nameFormatter,
					messageFormatter, throwableFormatter, keyValuesFormatter);
		}

	}

}

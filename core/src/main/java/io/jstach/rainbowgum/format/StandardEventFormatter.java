package io.jstach.rainbowgum.format;

public class StandardEventFormatter extends AbstractStandardEventFormatter {

	protected StandardEventFormatter(LevelFormatter levelFormatter, InstantFormatter instantFormatter,
			NameFormatter nameFormatter, ThrowableFormatter throwableFormatter, KeyValuesFormatter keyValuesFormatter,
			ThreadFormatter threadFormatter) {
		super(levelFormatter, instantFormatter, nameFormatter, throwableFormatter, keyValuesFormatter, threadFormatter);
	}

	public static Builder builder() {
		return new Builder();
	}

	public static class Builder extends AbstractBuilder<Builder> {

		@Override
		protected Builder self() {
			return this;
		}

		public StandardEventFormatter build() {
			return new StandardEventFormatter(levelFormatter, instantFormatter, nameFormatter, throwableFormatter,
					keyValuesFormatter, threadFormatter);
		}

	}

}

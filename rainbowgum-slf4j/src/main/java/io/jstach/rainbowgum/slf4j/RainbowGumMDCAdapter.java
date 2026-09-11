package io.jstach.rainbowgum.slf4j;

class RainbowGumMDCAdapter extends ArrayMDCAdapter {

	RainbowGumMDCAdapter() {
		super();
	}

	/**
	 * @param disabled when {@code true}, every mutating/reading method becomes a
	 * no-op/empty-returning stub and neither {@link ThreadLocal} in
	 * {@link ArrayMDCAdapter} is ever touched - see
	 * {@link RainbowGumSLF4JServiceProvider} for where {@code logging.mdc.disabled} is
	 * read and this constructor called.
	 */
	RainbowGumMDCAdapter(boolean disabled) {
		super(disabled);
	}

}
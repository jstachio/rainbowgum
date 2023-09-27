package io.jstach.rainbowgum;

import java.util.function.Supplier;

import io.jstach.rainbowgum.format.StandardEventFormatter;

public enum Defaults {

	CONSOLE;

	Supplier<? extends LogFormatter> defaultFormatter;

	Defaults() {
		defaultFormatter = () -> StandardEventFormatter.builder().build();
	}

	public void setDefaultFormatter(Supplier<? extends LogFormatter> defaultFormatter) {
		this.defaultFormatter = defaultFormatter;
	}

}

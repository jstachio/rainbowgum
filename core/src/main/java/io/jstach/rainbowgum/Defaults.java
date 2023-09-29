package io.jstach.rainbowgum;

import java.util.function.Function;
import java.util.function.Supplier;

import io.jstach.rainbowgum.format.StandardEventFormatter;

public enum Defaults {

	CONSOLE;

	public static String SHUTDOWN = "#SHUTDOWN#";

	Supplier<? extends LogFormatter> defaultFormatter;

	public static Function<LogConfig, ? extends Runnable> shutdownHook = (config) -> {
		return () -> {
		};
	};

	public static Supplier<? extends LogConfig> config = () -> {
		return LogConfig.of();
	};

	Defaults() {
		defaultFormatter = () -> StandardEventFormatter.builder().build();
	}

	public void setDefaultFormatter(Supplier<? extends LogFormatter> defaultFormatter) {
		this.defaultFormatter = defaultFormatter;
	}

}

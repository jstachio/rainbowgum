package io.jstach.rainbowgum.benchmark.nativeimage.rainbowgum;

import io.jstach.rainbowgum.LogConfig;
import io.jstach.rainbowgum.spi.RainbowGumServiceProvider;

/**
 * Registers the {@value StringStdOutOutput#SCHEME} URI scheme so
 * {@code logging.appender.console.output=string-stdout:///} resolves to a
 * {@link StringStdOutOutput}. Registered via a hand-written
 * {@code META-INF/services/io.jstach.rainbowgum.spi.RainbowGumServiceProvider} entry
 * (this benchmark module has no annotation processor wired up for
 * {@code @ServiceProvider}, unlike core's other optional modules).
 */
public final class StringStdOutConfigurator implements RainbowGumServiceProvider.Configurator {

	/**
	 * No arg for service loader.
	 */
	public StringStdOutConfigurator() {
	}

	@Override
	public boolean configure(LogConfig config, Pass pass) {
		config.outputRegistry().register(StringStdOutOutput.SCHEME, ref -> (name, c) -> new StringStdOutOutput());
		return true;
	}

}

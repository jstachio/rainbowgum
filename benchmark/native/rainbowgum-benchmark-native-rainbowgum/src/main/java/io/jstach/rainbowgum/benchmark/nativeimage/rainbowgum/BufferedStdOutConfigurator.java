package io.jstach.rainbowgum.benchmark.nativeimage.rainbowgum;

import io.jstach.rainbowgum.LogConfig;
import io.jstach.rainbowgum.spi.RainbowGumServiceProvider;

/**
 * Registers the {@value BufferedStdOutOutput#SCHEME} URI scheme so
 * {@code logging.appender.console.output=buffered-stdout:///} resolves to a
 * {@link BufferedStdOutOutput}. Registered via a hand-written
 * {@code META-INF/services/io.jstach.rainbowgum.spi.RainbowGumServiceProvider} entry,
 * same pattern as {@link StringStdOutConfigurator}.
 */
public final class BufferedStdOutConfigurator implements RainbowGumServiceProvider.Configurator {

	/**
	 * No arg for service loader.
	 */
	public BufferedStdOutConfigurator() {
	}

	@Override
	public boolean configure(LogConfig config, Pass pass) {
		config.outputRegistry().register(BufferedStdOutOutput.SCHEME, ref -> (name, c) -> new BufferedStdOutOutput());
		return true;
	}

}

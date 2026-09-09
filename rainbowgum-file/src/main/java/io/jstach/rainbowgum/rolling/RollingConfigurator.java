package io.jstach.rainbowgum.rolling;

import io.jstach.rainbowgum.LogConfig;
import io.jstach.rainbowgum.LogOutput;
import io.jstach.rainbowgum.file.FileOutput;
import io.jstach.rainbowgum.spi.RainbowGumServiceProvider;
import io.jstach.svc.ServiceProvider;

/**
 * Registers the two URI schemes {@code rainbowgum-file} provides:
 * {@value LogOutput#FILE_SCHEME} (so {@code file:///path/to/app.log} resolves to a
 * {@link FileOutput} - {@code core} on its own has no such registration) and
 * {@value RollingFileOutput#ROLLING_SCHEME} (so {@code rolling:///path/to/app.log}
 * resolves to a {@link RollingFileOutput}).
 */
@ServiceProvider(RainbowGumServiceProvider.class)
public class RollingConfigurator implements RainbowGumServiceProvider.Configurator {

	/**
	 * No arg for service loader.
	 */
	public RollingConfigurator() {
	}

	@Override
	public boolean configure(LogConfig config, Pass pass) {
		config.outputRegistry().register(LogOutput.FILE_SCHEME, FileOutput::of);
		config.outputRegistry().register(RollingFileOutput.ROLLING_SCHEME, RollingFileOutput::of);
		return true;
	}

}

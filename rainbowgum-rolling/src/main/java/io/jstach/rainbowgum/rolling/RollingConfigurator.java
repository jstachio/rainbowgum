package io.jstach.rainbowgum.rolling;

import io.jstach.rainbowgum.LogConfig;
import io.jstach.rainbowgum.spi.RainbowGumServiceProvider;
import io.jstach.svc.ServiceProvider;

/**
 * Registers the {@value RollingFileOutput#ROLLING_SCHEME} URI scheme so
 * {@code rolling:///path/to/app.log} resolves to a {@link RollingFileOutput}.
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
		config.outputRegistry().register(RollingFileOutput.ROLLING_SCHEME, RollingFileOutput::of);
		return true;
	}

}

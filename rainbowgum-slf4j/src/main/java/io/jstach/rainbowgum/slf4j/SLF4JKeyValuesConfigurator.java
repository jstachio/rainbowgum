package io.jstach.rainbowgum.slf4j;

import io.jstach.rainbowgum.LogConfig;
import io.jstach.rainbowgum.LogEventFactory.KeyValuesContributor;
import io.jstach.rainbowgum.spi.RainbowGumServiceProvider;
import io.jstach.svc.ServiceProvider;

/**
 * Registers SLF4J's MDC as a {@link KeyValuesContributor} for
 * {@link KeyValuesContributor.Source.Standard#SLF4J} so events from other logging APIs
 * (JUL, System.Logger, ...) carry the current MDC as well.
 */
@ServiceProvider(RainbowGumServiceProvider.class)
public final class SLF4JKeyValuesConfigurator implements RainbowGumServiceProvider.Configurator {

	/**
	 * For service loader.
	 */
	public SLF4JKeyValuesConfigurator() {
	}

	@Override
	public boolean configure(LogConfig config, Pass pass) {
		KeyValuesContributor.register(config.serviceRegistry(), KeyValuesContributor.Source.Standard.SLF4J,
				RainbowGumSLF4JServiceProvider::currentMDCKeyValues);
		return true;
	}

}

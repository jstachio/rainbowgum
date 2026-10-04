package io.jstach.rainbowgum.slf4j;

import io.jstach.rainbowgum.LogConfig;
import io.jstach.rainbowgum.LogEventFactory.KeyValuesContributor;
import io.jstach.rainbowgum.spi.RainbowGumServiceProvider;
import io.jstach.svc.ServiceProvider;

/**
 * Registers SLF4J's MDC as a {@link KeyValuesContributor} named
 * {@value RainbowGumSLF4JServiceProvider#KEY_VALUES_CONTRIBUTOR_NAME} so events from
 * other logging APIs (JUL, System.Logger, ...) carry the current MDC as well.
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
		config.serviceRegistry()
			.put(KeyValuesContributor.class, RainbowGumSLF4JServiceProvider.KEY_VALUES_CONTRIBUTOR_NAME,
					RainbowGumSLF4JServiceProvider::currentMDCKeyValues);
		return true;
	}

}

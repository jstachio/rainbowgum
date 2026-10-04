package io.jstach.rainbowgum.log4j;

import io.jstach.rainbowgum.LogConfig;
import io.jstach.rainbowgum.LogEventFactory.KeyValuesContributor;
import io.jstach.rainbowgum.spi.RainbowGumServiceProvider;
import io.jstach.svc.ServiceProvider;

/**
 * Registers Log4j's {@code ThreadContext} as a {@link KeyValuesContributor} named
 * {@value RainbowGumLog4jProvider#KEY_VALUES_CONTRIBUTOR_NAME} so events from other
 * logging APIs carry it as well.
 */
@ServiceProvider(RainbowGumServiceProvider.class)
public final class Log4jKeyValuesConfigurator implements RainbowGumServiceProvider.Configurator {

	/**
	 * For service loader.
	 */
	public Log4jKeyValuesConfigurator() {
	}

	@Override
	public boolean configure(LogConfig config, Pass pass) {
		config.serviceRegistry()
			.put(KeyValuesContributor.class, RainbowGumLog4jProvider.KEY_VALUES_CONTRIBUTOR_NAME,
					RainbowGumLog4jProvider::currentThreadContextKeyValues);
		return true;
	}

}

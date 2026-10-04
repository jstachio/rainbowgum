package io.jstach.rainbowgum.jbosslogging;

import io.jstach.rainbowgum.LogConfig;
import io.jstach.rainbowgum.LogEventFactory.KeyValuesContributor;
import io.jstach.rainbowgum.spi.RainbowGumServiceProvider;
import io.jstach.svc.ServiceProvider;

/**
 * Registers JBoss Logging's MDC as a {@link KeyValuesContributor} named
 * {@value RainbowGumJBossLoggerProvider#KEY_VALUES_CONTRIBUTOR_NAME} so events from other
 * logging APIs carry it as well.
 */
@ServiceProvider(RainbowGumServiceProvider.class)
public final class JBossKeyValuesConfigurator implements RainbowGumServiceProvider.Configurator {

	/**
	 * For service loader.
	 */
	public JBossKeyValuesConfigurator() {
	}

	@Override
	public boolean configure(LogConfig config, Pass pass) {
		config.serviceRegistry()
			.put(KeyValuesContributor.class, RainbowGumJBossLoggerProvider.KEY_VALUES_CONTRIBUTOR_NAME,
					RainbowGumJBossLoggerProvider::currentMDCKeyValues);
		return true;
	}

}

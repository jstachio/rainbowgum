package io.jstach.rainbowgum.jbosslogging;

import io.jstach.rainbowgum.KeyValues;
import io.jstach.rainbowgum.LogConfig;
import io.jstach.rainbowgum.LogEventFactory.KeyValuesContributor;
import io.jstach.rainbowgum.spi.RainbowGumServiceProvider;
import io.jstach.svc.ServiceProvider;

/**
 * Registers JBoss Logging's MDC as a {@link KeyValuesContributor} for
 * {@link KeyValuesContributor.Source.Standard#JBOSS_LOGGING} so events from other logging
 * APIs carry it as well.
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
		KeyValuesContributor.register(config.serviceRegistry(), KeyValuesContributor.Source.Standard.JBOSS_LOGGING,
				MDCContributor.INSTANCE);
		return true;
	}

	private enum MDCContributor implements KeyValuesContributor {

		INSTANCE;

		@Override
		public KeyValues keyValues() {
			return RainbowGumJBossLoggerProvider.currentMDCKeyValues();
		}

		@Override
		public void clear() {
			RainbowGumJBossLoggerProvider.clearCurrentThreadContext();
		}

	}

}

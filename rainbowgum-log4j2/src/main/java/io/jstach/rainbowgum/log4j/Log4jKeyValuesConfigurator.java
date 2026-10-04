package io.jstach.rainbowgum.log4j;

import io.jstach.rainbowgum.KeyValues;
import io.jstach.rainbowgum.LogConfig;
import io.jstach.rainbowgum.LogEventFactory.KeyValuesContributor;
import io.jstach.rainbowgum.spi.RainbowGumServiceProvider;
import io.jstach.svc.ServiceProvider;

/**
 * Registers Log4j's {@code ThreadContext} as a {@link KeyValuesContributor} for
 * {@link KeyValuesContributor.Source.Standard#LOG4J2} so events from other logging APIs
 * carry it as well.
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
		KeyValuesContributor.register(config.serviceRegistry(), KeyValuesContributor.Source.Standard.LOG4J2,
				ThreadContextContributor.INSTANCE);
		return true;
	}

	private enum ThreadContextContributor implements KeyValuesContributor {

		INSTANCE;

		@Override
		public KeyValues keyValues() {
			return RainbowGumLog4jProvider.currentThreadContextKeyValues();
		}

		@Override
		public void clear() {
			RainbowGumLog4jProvider.clearThreadContext();
		}

	}

}

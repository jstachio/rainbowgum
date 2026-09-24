package io.jstach.rainbowgum.scopedkeyvalues.provider;

import io.jstach.rainbowgum.LogConfig;
import io.jstach.rainbowgum.LogEventFactory;
import io.jstach.rainbowgum.scopedkeyvalues.ScopedKeyValues;
import io.jstach.rainbowgum.slf4j.RainbowGumSLF4JServiceProvider;
import io.jstach.rainbowgum.spi.RainbowGumServiceProvider;
import io.jstach.svc.ServiceProvider;

/**
 * Registers {@link ScopedKeyValuesLogEventFactory} in {@link LogConfig#serviceRegistry()}
 * under {@code RainbowGumSLF4JServiceProvider#SCOPED_KEY_VALUES_SERVICE_NAME} - the one
 * thing this module needs to do for {@code rainbowgum-slf4j} to start merging
 * {@link ScopedKeyValues} into every event it builds, underneath MDC. Simply having this
 * module on the classpath is enough - no configuration, no properties.
 */
@ServiceProvider(RainbowGumServiceProvider.class)
public final class ScopedKeyValuesConfigurator implements RainbowGumServiceProvider.Configurator {

	/**
	 * For service loader.
	 */
	public ScopedKeyValuesConfigurator() {
	}

	@Override
	public boolean configure(@SuppressWarnings("exports") LogConfig config, @SuppressWarnings("exports") Pass pass) {
		config.serviceRegistry()
			.put(LogEventFactory.class, RainbowGumSLF4JServiceProvider.SCOPED_KEY_VALUES_SERVICE_NAME,
					ScopedKeyValuesLogEventFactory.INSTANCE);
		return true;
	}

}

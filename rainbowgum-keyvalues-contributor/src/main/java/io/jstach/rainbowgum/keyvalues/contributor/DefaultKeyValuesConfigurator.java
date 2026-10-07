package io.jstach.rainbowgum.keyvalues.contributor;

import java.util.Map;

import io.jstach.rainbowgum.KeyValues;
import io.jstach.rainbowgum.LogConfig;
import io.jstach.rainbowgum.LogEventFactory.KeyValuesContributor;
import io.jstach.rainbowgum.spi.RainbowGumServiceProvider;
import io.jstach.svc.ServiceProvider;

/**
 * Registers {@link DefaultKeyValues} as the
 * {@link KeyValuesContributor.Source.Standard#DEFAULTS} key values source with the values
 * of {@value DefaultKeyValues#DEFAULTS_PROPERTY}. Simply having this module on the
 * classpath is enough.
 */
@ServiceProvider(RainbowGumServiceProvider.class)
public final class DefaultKeyValuesConfigurator implements RainbowGumServiceProvider.Configurator {

	/**
	 * For service loader.
	 */
	public DefaultKeyValuesConfigurator() {
	}

	@Override
	public boolean configure(@SuppressWarnings("exports") LogConfig config, @SuppressWarnings("exports") Pass pass) {
		Map<String, String> values = config.properties()
			.forKey(DefaultKeyValues.DEFAULTS_PROPERTY)
			.ofMap()
			.or(Map.of())
			.validateNow(DefaultKeyValuesConfigurator.class);
		var defaults = DefaultKeyValues.of(config.serviceRegistry());
		defaults.configure(KeyValues.of(values));
		KeyValuesContributor.register(config.serviceRegistry(), KeyValuesContributor.Source.Standard.DEFAULTS,
				defaults);
		return true;
	}

}

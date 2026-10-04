package io.jstach.rainbowgum.scopedkeyvalues.provider;

import io.jstach.rainbowgum.LogConfig;
import io.jstach.rainbowgum.LogEventFactory.KeyValuesContributor;
import io.jstach.rainbowgum.scopedkeyvalues.ScopedKeyValues;
import io.jstach.rainbowgum.spi.RainbowGumServiceProvider;
import io.jstach.svc.ServiceProvider;

/**
 * Registers {@link ScopedKeyValues} as a {@link KeyValuesContributor} named
 * {@value #KEY_VALUES_CONTRIBUTOR_NAME}, so every logging API Rainbow Gum supports picks
 * up the current scoped key values. Simply having this module on the classpath is enough:
 * no configuration, no properties.
 */
@ServiceProvider(RainbowGumServiceProvider.class)
public final class ScopedKeyValuesConfigurator implements RainbowGumServiceProvider.Configurator {

	/**
	 * The name scoped key values are registered under as a {@link KeyValuesContributor}.
	 */
	public static final String KEY_VALUES_CONTRIBUTOR_NAME = "scopedKeyValues";

	/**
	 * For service loader.
	 */
	public ScopedKeyValuesConfigurator() {
	}

	@Override
	public boolean configure(@SuppressWarnings("exports") LogConfig config, @SuppressWarnings("exports") Pass pass) {
		config.serviceRegistry()
			.put(KeyValuesContributor.class, KEY_VALUES_CONTRIBUTOR_NAME, ScopedKeyValuesContributor.INSTANCE);
		return true;
	}

}

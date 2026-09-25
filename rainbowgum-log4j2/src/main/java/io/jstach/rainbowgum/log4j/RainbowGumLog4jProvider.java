package io.jstach.rainbowgum.log4j;

import org.apache.logging.log4j.spi.Provider;

import io.jstach.svc.ServiceProvider;

/**
 * Registered via {@link java.util.ServiceLoader} ({@code META-INF/services/}
 * {@link Provider org.apache.logging.log4j.spi.Provider}), not the older
 * {@code META-INF/log4j-provider.properties} mechanism: decompiling this version of
 * {@code log4j-api} shows that path's {@code Provider(Properties, URL, ClassLoader)}
 * constructor unconditionally sets {@code versions} to <code>null</code>, which
 * {@code ProviderUtil}/{@code validVersion} then rejects outright ("Ignoring provider for
 * incompatible version"), confirmed empirically: a properties-file-registered provider is
 * silently never selected. The subclass-plus-{@link java.util.ServiceLoader} path used
 * here still populates {@link #getVersions()} correctly (from the
 * {@link Provider#Provider(Integer, String, Class) Provider(Integer, String, Class)}
 * constructor called below), so it is picked up correctly.
 */
@ServiceProvider(Provider.class)
public final class RainbowGumLog4jProvider extends Provider {

	/**
	 * For {@link java.util.ServiceLoader}.
	 */
	public RainbowGumLog4jProvider() {
		super(10, CURRENT_VERSION, RainbowGumLoggerContextFactory.class);
	}

}

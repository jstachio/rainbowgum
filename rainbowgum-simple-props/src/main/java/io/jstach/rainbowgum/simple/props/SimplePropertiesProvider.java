package io.jstach.rainbowgum.simple.props;

import java.util.List;

import io.jstach.rainbowgum.LogAlerts;
import io.jstach.rainbowgum.LogConfig;
import io.jstach.rainbowgum.LogProperties;
import io.jstach.rainbowgum.ServiceRegistry;
import io.jstach.rainbowgum.spi.RainbowGumServiceProvider;
import io.jstach.svc.ServiceProvider;

/**
 * Makes {@link SimpleProperties} provide properties to RainbowGum.
 * <p>
 * If a {@link SimpleProperties} is already bound in the {@link ServiceRegistry} (for
 * example an application put one there with a custom
 * {@link SimpleProperties.Builder#envPrefix(String) envPrefix}/
 * {@link SimpleProperties.Builder#resource(String) resource} before RainbowGum
 * initializes) it is used instead of the default {@link SimpleProperties#builder()
 * SimpleProperties.builder().build()}.
 */
@ServiceProvider(RainbowGumServiceProvider.class)
public final class SimplePropertiesProvider
		implements RainbowGumServiceProvider.PropertiesProvider, RainbowGumServiceProvider.Configurator {

	/**
	 * For service loader.
	 */
	public SimplePropertiesProvider() {
	}

	@Override
	public List<LogProperties> provideProperties(ServiceRegistry registry, LogAlerts alerts) {
		var simpleProperties = registry.putIfAbsent(SimpleProperties.class, () -> SimpleProperties.builder().build());
		simpleProperties.reportAlerts(alerts);
		return simpleProperties.properties();
	}

	/*
	 * Starts watching the level file, if enabled, once the configuration exists to
	 * publish changes to; the watcher stops when the configuration closes.
	 */
	@Override
	public boolean configure(LogConfig config, Pass pass) {
		var simpleProperties = config.serviceRegistry().findOrNull(SimpleProperties.class);
		var levelFile = simpleProperties == null ? null : simpleProperties.levelFile();
		if (levelFile != null) {
			levelFile.watch(config);
			config.serviceRegistry().onClose(levelFile);
		}
		return true;
	}

}

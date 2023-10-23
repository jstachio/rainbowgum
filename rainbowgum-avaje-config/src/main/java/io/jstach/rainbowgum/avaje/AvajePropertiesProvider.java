package io.jstach.rainbowgum.avaje;

import java.util.List;

import org.eclipse.jdt.annotation.Nullable;

import io.avaje.config.Config;
import io.avaje.config.Configuration;
import io.jstach.rainbowgum.LogConfig;
import io.jstach.rainbowgum.LogProperties;
import io.jstach.rainbowgum.ServiceRegistry;
import io.jstach.rainbowgum.spi.RainbowGumServiceProvider;
import io.jstach.svc.ServiceProvider;

@ServiceProvider(RainbowGumServiceProvider.class)
public class AvajePropertiesProvider
		implements RainbowGumServiceProvider.PropertiesProvider, RainbowGumServiceProvider.Initializer {

	@Override
	public List<LogProperties> provideProperties(ServiceRegistry registry) {
		var props = new AvajeProperties(Config.asConfiguration());
		registry.put(AvajeProperties.class, props);
		return List.of(props);
	}

	@Override
	public void initialize(ServiceRegistry registry, LogConfig config) {
		var props = registry.findOrNull(AvajeProperties.class);
		if (props != null) {
			props.configuration.onChange(e -> {
				config.publisher().publish();
			});
		}
	}

}

class AvajeProperties implements LogProperties {

	final Configuration configuration;

	public AvajeProperties(Configuration configuration) {
		super();
		this.configuration = configuration;
	}

	@Override
	public @Nullable String valueOrNull(String key) {
		return configuration.getNullable(key);
	}

}

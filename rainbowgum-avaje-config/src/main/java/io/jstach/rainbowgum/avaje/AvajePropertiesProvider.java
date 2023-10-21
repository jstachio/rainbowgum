package io.jstach.rainbowgum.avaje;

import java.util.List;

import org.eclipse.jdt.annotation.Nullable;

import io.avaje.config.Config;
import io.avaje.config.Configuration;
import io.jstach.rainbowgum.LogProperties;
import io.jstach.rainbowgum.spi.RainbowGumServiceProvider;
import io.jstach.svc.ServiceProvider;

@ServiceProvider(RainbowGumServiceProvider.class)
public class AvajePropertiesProvider implements RainbowGumServiceProvider.PropertiesProvider {

	@Override
	public List<LogProperties> provideProperties() {
		var props = new AvajeProperties(Config.asConfiguration());
		return List.of(props);
	}

}

class AvajeProperties implements LogProperties {

	private final Configuration configuration;

	public AvajeProperties(Configuration configuration) {
		super();
		this.configuration = configuration;
	}

	@Override
	public @Nullable String valueOrNull(String key) {
		return configuration.getNullable(key);
	}

}

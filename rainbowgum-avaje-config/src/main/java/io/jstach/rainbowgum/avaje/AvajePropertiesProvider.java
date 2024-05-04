package io.jstach.rainbowgum.avaje;

import java.util.List;
import java.util.function.Supplier;

import org.eclipse.jdt.annotation.Nullable;

import io.avaje.config.Config;
import io.avaje.config.Configuration;
import io.jstach.rainbowgum.LogConfig;
import io.jstach.rainbowgum.LogProperties;
import io.jstach.rainbowgum.ServiceRegistry;
import io.jstach.rainbowgum.spi.RainbowGumServiceProvider;
import io.jstach.svc.ServiceProvider;

/**
 * Makes avaje provide properties to RainbowGum.
 * <p>
 * If {@link io.avaje.config.Configuration} is already bound in the
 * {@link ServiceRegistry} it will be used instead of the Avaje's default static bound
 * Configuration.
 */
@ServiceProvider(RainbowGumServiceProvider.class)
public final class AvajePropertiesProvider
		implements RainbowGumServiceProvider.PropertiesProvider, RainbowGumServiceProvider.Configurator {

	private final Supplier<Configuration> configurationSupplier;

	/**
	 * For service loader.
	 */
	public AvajePropertiesProvider() {
		this(() -> Config.asConfiguration());
	}

	AvajePropertiesProvider(Supplier<Configuration> configurationSupplier) {
		super();
		this.configurationSupplier = configurationSupplier;
	}

	@Override
	public List<LogProperties> provideProperties(ServiceRegistry registry) {
		var config = registry.putIfAbsent(Configuration.class, configurationSupplier);
		var props = new AvajeProperties(config);
		registry.put(AvajeProperties.class, props);
		return List.of(props);
	}

	@Override
	public boolean configure(LogConfig config, Pass pass) {
		var registry = config.serviceRegistry();
		var props = registry.findOrNull(AvajeProperties.class);
		if (props != null) {
			props.configuration.onChange(e -> {
				config.changePublisher().publish();
			});
		}
		return true;
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

	@Override
	public String description(String key) {
		var entry = configuration.entry(key).orElse(null);
		String source = entry == null ? "AVAJE" : "AVAJE(" + entry.source() + ")";
		return source + "[" + key + "]";
	}

}

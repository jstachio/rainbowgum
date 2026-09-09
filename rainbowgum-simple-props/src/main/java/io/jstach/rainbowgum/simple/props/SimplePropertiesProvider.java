package io.jstach.rainbowgum.simple.props;

import java.util.List;

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
public final class SimplePropertiesProvider implements RainbowGumServiceProvider.PropertiesProvider {

	/**
	 * For service loader.
	 */
	public SimplePropertiesProvider() {
	}

	@Override
	public List<LogProperties> provideProperties(ServiceRegistry registry) {
		var simpleProperties = registry.putIfAbsent(SimpleProperties.class, () -> SimpleProperties.builder().build());
		return simpleProperties.properties();
	}

}

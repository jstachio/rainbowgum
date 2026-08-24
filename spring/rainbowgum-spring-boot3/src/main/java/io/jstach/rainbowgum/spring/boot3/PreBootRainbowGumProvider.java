package io.jstach.rainbowgum.spring.boot3;

import java.util.List;
import java.util.Optional;

import io.jstach.rainbowgum.LogConfig;
import io.jstach.rainbowgum.LogProperties;
import io.jstach.rainbowgum.RainbowGum;
import io.jstach.rainbowgum.ServiceRegistry;
import io.jstach.rainbowgum.spi.RainbowGumServiceProvider;
import io.jstach.rainbowgum.spi.RainbowGumServiceProvider.PropertiesProvider;
import io.jstach.rainbowgum.spi.RainbowGumServiceProvider.RainbowGumProvider;
import io.jstach.svc.ServiceProvider;

/**
 * Provides a Rainbow Gum that is used during the pre boot process of Spring.
 */
@ServiceProvider(RainbowGumServiceProvider.class)
public class PreBootRainbowGumProvider implements RainbowGumProvider, PropertiesProvider {

	/**
	 * For service loader.
	 */
	public PreBootRainbowGumProvider() {
	}

	@Override
	public Optional<RainbowGum> provide(LogConfig config) {
		config.serviceRegistry().put(BootFlag.class, BootFlag.INSTANCE);
		return Optional.of(RainbowGum.queued(config));
	}

	enum BootFlag {

		INSTANCE;

	}

	@Override
	public List<LogProperties> provideProperties(ServiceRegistry registry) {
		/*
		 * We need to disable JUL because for whatever reasons rainbowgums version is slow
		 * on Spring Boot.
		 *
		 * We also need to have changing level loggers so we do not miss anything.
		 *
		 * However any loggers used after Spring Boots initialization should be level
		 * loggers assuming configuration is not different.
		 */
		String changeProperties = """
				logging.jul.disable=true
				logging.global.change=true
				logging.change=level
				""";

		var properties = LogProperties.builder()
			.fromProperties(changeProperties)
			.description("PreBoot properties")
			.build();

		return List.of(LogProperties.StandardProperties.SYSTEM_PROPERTIES, properties);
	}

}

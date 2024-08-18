package io.jstach.rainbowgum.spring.boot;

import java.util.Optional;

import io.jstach.rainbowgum.LogConfig;
import io.jstach.rainbowgum.LogProperties;
import io.jstach.rainbowgum.RainbowGum;
import io.jstach.rainbowgum.spi.RainbowGumServiceProvider;
import io.jstach.rainbowgum.spi.RainbowGumServiceProvider.RainbowGumProvider;
import io.jstach.svc.ServiceProvider;

/**
 * Provides a Rainbow Gum that is used during the pre boot process of Spring.
 */
@ServiceProvider(RainbowGumServiceProvider.class)
public class PreBootRainbowGumProvider implements RainbowGumProvider {

	/**
	 * For service loader.
	 */
	public PreBootRainbowGumProvider() {
	}

	@Override
	public Optional<RainbowGum> provide(LogConfig config) {
		String changeProperties = """
				logging.global.change=true
				logging.change=level
				""";

		var properties = LogProperties.builder()
			// .fromFunction(System::getProperty)
			// .removeKeyPrefix("boot.logging.")
			.fromProperties(changeProperties)
			.description("PreBoot properties")
			.build();
		var newConfig = LogConfig.builder().properties(properties).build();
		newConfig.serviceRegistry().put(BootFlag.class, BootFlag.INSTANCE);
		return Optional.of(RainbowGum.queued(newConfig));
	}

	enum BootFlag {

		INSTANCE;

	}

}

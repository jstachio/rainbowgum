package io.jstach.rainbowgum.spring.boot.spi;

import java.util.Optional;

import org.springframework.core.env.Environment;

import io.jstach.rainbowgum.LogConfig;
import io.jstach.rainbowgum.RainbowGum;
import io.jstach.rainbowgum.spi.RainbowGumServiceProvider;
import io.jstach.rainbowgum.spi.RainbowGumServiceProvider.RainbowGumProvider;

/**
 * Provides a custom Spring Boot aware RainbowGum through
 * <code>META-INF/spring.factories</code>. The reason this SPI exists is if one wants to
 * create a custom Rainbow Gum that has spring configuration available. Besides providing
 * Spring environment the other reason the {@link RainbowGumServiceProvider} is not used
 * is because this module has a registration of {@link RainbowGumProvider} that is used
 * for Spring Boot initialization (the events are queued) that is then replaced with the
 * Spring Boot configuration powered RainbowGum.
 */
public interface SpringRainbowGumServiceProvider {

	/**
	 * Provides a Rainbow Gum that has configuration already Spring Bootified.
	 * @param config config that is setup to use Spring Boot environment.
	 * @param classLoader class loader used by Spring Boot.
	 * @param environment Spring environment.
	 * @return optional provides.
	 */
	public Optional<RainbowGum> provide(LogConfig config, ClassLoader classLoader,
			@SuppressWarnings("exports") Environment environment);

}

package io.jstach.rainbowgum.slf4j;

import io.jstach.rainbowgum.spi.RainbowGumServiceProvider.RainbowGumEagerLoad;
import io.jstach.svc.ServiceProvider;

/**
 * This is an internal detail to signal that this SLF4J implementation will load Rainbow
 * Gum.
 *
 * @hidden
 */
@ServiceProvider(io.jstach.rainbowgum.spi.RainbowGumServiceProvider.class)
public final class SLF4JRainbowGumEagerLoad implements RainbowGumEagerLoad {

	/**
	 * no arg constructor required for service loader however it will unlikely be called.
	 */
	public SLF4JRainbowGumEagerLoad() {
	}

}

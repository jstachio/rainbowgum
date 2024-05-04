package io.jstach.rainbowgum.pattern.format.spi;

import io.jstach.rainbowgum.LogConfig;
import io.jstach.rainbowgum.pattern.format.PatternRegistry;
import io.jstach.rainbowgum.spi.RainbowGumServiceProvider;
import io.jstach.rainbowgum.spi.RainbowGumServiceProvider.Configurator;

/**
 * Extend for a service provider that will register custom keywords.
 *
 * @see RainbowGumServiceProvider
 */
public abstract class PatternKeywordProvider implements Configurator {

	/**
	 * Called by service loader.
	 */
	public PatternKeywordProvider() {
	}

	@Override
	public boolean configure(LogConfig config, Pass pass) {
		PatternRegistry patternRegistry = config.serviceRegistry().findOrNull(PatternRegistry.class);
		if (patternRegistry == null) {
			return false;
		}
		register(patternRegistry);
		return true;
	}

	/**
	 * Registers formatter factories with keywords.
	 * @param patternRegistry not null.
	 */
	protected abstract void register(PatternRegistry patternRegistry);

}

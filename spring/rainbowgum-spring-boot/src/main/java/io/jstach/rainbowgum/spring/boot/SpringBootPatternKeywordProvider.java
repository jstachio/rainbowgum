package io.jstach.rainbowgum.spring.boot;

import io.jstach.rainbowgum.pattern.format.PatternRegistry;
import io.jstach.rainbowgum.pattern.format.spi.PatternKeywordProvider;

/*
 * Normally this is registered with the service loader
 * but we keep it boot style.
 */
class SpringBootPatternKeywordProvider extends PatternKeywordProvider {

	@Override
	protected void register(PatternRegistry patternRegistry) {
		for (var kf : SpringBootKeywordFactory.values()) {
			patternRegistry.register(kf);
		}

	}

}

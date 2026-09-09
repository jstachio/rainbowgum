package io.jstach.rainbowgum.simple.props;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

import io.jstach.rainbowgum.RainbowGum;

/*
 * Proves the full ServiceLoader wiring end to end - unlike SimplePropertiesTest (which
 * builds SimpleProperties directly), this goes through RainbowGum's own default
 * bootstrap (ServiceLoader.load(RainbowGumServiceProvider.class), see
 * LogConfig.Builder.serviceLoader()) so it only passes if SimplePropertiesProvider's
 * @ServiceProvider-generated META-INF/services entry is actually picked up. Uses a
 * try-with-resources RainbowGum so close() resets the global RainbowGumHolder (see
 * RainbowGum#close()) instead of leaking state into other test classes.
 */
class SimplePropertiesProviderTest {

	@Test
	void testServiceLoaderDiscoversProviderAndResolvesFromClasspathFile() {
		RainbowGum.set(RainbowGum::defaults);
		try (var gum = RainbowGum.of()) {
			// src/test/resources/logging.properties has logging.level.root=WARN
			String value = gum.config().properties().forKey("logging.level.root").ofString().value();
			assertEquals("WARN", value);
		}
	}

}

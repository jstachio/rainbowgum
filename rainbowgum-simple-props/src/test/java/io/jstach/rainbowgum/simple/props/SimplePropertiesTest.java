package io.jstach.rainbowgum.simple.props;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;

import org.junit.jupiter.api.Test;

import io.jstach.rainbowgum.LogProperties;
import io.jstach.rainbowgum.LogProperty.Result;

class SimplePropertiesTest {

	@Test
	void testEnvVarMappingDefaultPrefix() {
		var props = SimpleProperties.builder()
			.envLookup(Map.of("RAINBOWGUM_level_root", "DEBUG")::get)
			.resource("classpath:/does-not-exist.properties")
			.build();
		var composite = LogProperties.of(props.properties());
		assertEquals("DEBUG", composite.forKey("logging.level.root").ofString().value());
	}

	@Test
	void testEnvVarMappingCustomPrefix() {
		var props = SimpleProperties.builder()
			.envPrefix("MYCO_")
			.envLookup(Map.of("MYCO_level_root", "TRACE")::get)
			.resource("classpath:/does-not-exist.properties")
			.build();
		var composite = LogProperties.of(props.properties());
		assertEquals("TRACE", composite.forKey("logging.level.root").ofString().value());
	}

	@Test
	void testClasspathResourceFallback() {
		// default resource is src/test/resources/logging.properties
		// (logging.level.root=WARN);
		// no env value for the key, so the file layer should win.
		var props = SimpleProperties.builder().envLookup(k -> null).build();
		var composite = LogProperties.of(props.properties());
		assertEquals("WARN", composite.forKey("logging.level.root").ofString().value());
	}

	@Test
	void testMissingResourceDoesNotThrowAndFallsThrough() {
		var props = SimpleProperties.builder()
			.resource("classpath:/does-not-exist.properties")
			.envLookup(k -> null)
			.build();
		var composite = LogProperties.of(props.properties());
		assertTrue(composite.forKey("logging.level.root").ofString() instanceof Result.Missing<String>);
	}

	@Test
	void testEnvVarWinsOverClasspathFile() {
		var props = SimpleProperties.builder().envLookup(Map.of("RAINBOWGUM_level_root", "DEBUG")::get).build();
		var composite = LogProperties.of(props.properties());
		// file has WARN, env has DEBUG - env (order 300) beats the file layer (order
		// 100).
		assertEquals("DEBUG", composite.forKey("logging.level.root").ofString().value());
	}

	@Test
	void testSystemPropertyWinsOverEverything() {
		System.setProperty("logging.level.root", "ERROR");
		try {
			var props = SimpleProperties.builder().envLookup(Map.of("RAINBOWGUM_level_root", "DEBUG")::get).build();
			var composite = LogProperties.of(props.properties());
			// sysprop (order 400) beats both env (DEBUG) and the file (WARN).
			assertEquals("ERROR", composite.forKey("logging.level.root").ofString().value());
		}
		finally {
			System.clearProperty("logging.level.root");
		}
	}

}

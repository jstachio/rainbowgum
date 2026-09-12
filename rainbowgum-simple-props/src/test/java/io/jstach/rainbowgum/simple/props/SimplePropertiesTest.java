package io.jstach.rainbowgum.simple.props;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.junit.jupiter.api.parallel.Isolated;

import io.jstach.rainbowgum.LogProperties;
import io.jstach.rainbowgum.LogProperty.PropertyConvertException;
import io.jstach.rainbowgum.LogProperty.Result;

/*
 * testSystemPropertyWinsOverEverything sets a real System property (JVM-wide global
 * state) - @Isolated keeps this from racing any other test class that reads/sets system
 * properties under the "fast" profile's parallel test execution (see
 * QueueRouterLevelPropertyInvalidTest in test/rainbowgum-test-core for the same pattern);
 * SAME_THREAD keeps this class's own methods from racing each other/that property.
 */
@Isolated
@Execution(ExecutionMode.SAME_THREAD)
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
	void testEnvVarTranslateKeyDoesNotStripNonRootPrefixedKey() {
		// direct unit test of the branch composite lookups never reach, since every
		// real property key starts with LogProperties.ROOT_PREFIX ("logging.") - the
		// key is used as-is (only "." to "_" still applies) instead of having a
		// "logging." lead segment stripped that was never there. EnvVarProperties is
		// package-private so this can only be exercised from here.
		var env = new EnvVarProperties("MYCO_", k -> null);
		assertEquals("MYCO_not_rooted", env.translateKey("not.rooted"));
	}

	@Test
	void testEnvVarBadIntValueFailsLoudlyWithEnvDescription() {
		var props = SimpleProperties.builder()
			.envLookup(Map.of("RAINBOWGUM_threshold", "not-a-number")::get)
			.resource("classpath:/does-not-exist.properties")
			.build();
		var composite = LogProperties.of(props.properties());
		var e = assertThrows(PropertyConvertException.class,
				() -> composite.forKey("logging.threshold").ofInt().value());
		assertEquals(
				"""
						Error for property. key: 'logging.threshold' from ENV[RAINBOWGUM_threshold], java.lang.NumberFormatException For input string: "not-a-number"
						Tried: 'logging.threshold' from SYSTEM_PROPERTIES[logging.threshold], ENV[RAINBOWGUM_threshold]""",
				e.getMessage());
	}

	@Test
	void testClasspathFileBadIntValueFailsLoudlyWithFileDescription() {
		var props = SimpleProperties.builder().resource("classpath:/bad-int.properties").envLookup(k -> null).build();
		var composite = LogProperties.of(props.properties());
		var e = assertThrows(PropertyConvertException.class,
				() -> composite.forKey("logging.threshold").ofInt().value());
		assertEquals(
				"""
						Error for property. key: 'logging.threshold' from SIMPLE_PROPS_FILE[classpath:/bad-int.properties][logging.threshold], java.lang.NumberFormatException For input string: "not-a-number"
						Tried: 'logging.threshold' from SYSTEM_PROPERTIES[logging.threshold], ENV[RAINBOWGUM_threshold], SIMPLE_PROPS_FILE[classpath:/bad-int.properties][logging.threshold]""",
				e.getMessage());
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

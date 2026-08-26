package io.jstach.rainbowgum.spring.boot4;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.StandardEnvironment;

import io.jstach.rainbowgum.LogProperties;
import io.jstach.rainbowgum.spring.boot4.RainbowGumLoggingSystemFactory.Patterns;

class PatternsTest {

	private static final LogProperties EMPTY = key -> null;

	private static StandardEnvironment environment(Map<String, Object> properties) {
		var environment = new StandardEnvironment();
		environment.getPropertySources().addFirst(new MapPropertySource("test", properties));
		return environment;
	}

	@Test
	void bothIncludedByDefault() {
		var patterns = new Patterns(EMPTY, environment(Map.of()));
		assertTrue(patterns.NAME_AND_GROUP.contains("APPLICATION_NAME"));
		assertTrue(patterns.NAME_AND_GROUP.contains("APPLICATION_GROUP"));
	}

	@Test
	void applicationNameExcludedWhenDisabled() {
		var patterns = new Patterns(EMPTY,
				environment(Map.of(SpringBootSupportedProperties.INCLUDE_APPLICATION_NAME, "false")));
		assertFalse(patterns.NAME_AND_GROUP.contains("APPLICATION_NAME"));
		assertTrue(patterns.NAME_AND_GROUP.contains("APPLICATION_GROUP"));
	}

	@Test
	void applicationGroupExcludedWhenDisabled() {
		var patterns = new Patterns(EMPTY,
				environment(Map.of(SpringBootSupportedProperties.INCLUDE_APPLICATION_GROUP, "false")));
		assertTrue(patterns.NAME_AND_GROUP.contains("APPLICATION_NAME"));
		assertFalse(patterns.NAME_AND_GROUP.contains("APPLICATION_GROUP"));
	}

	@Test
	void bothExcludedWhenDisabled() {
		var patterns = new Patterns(EMPTY, environment(Map.of(SpringBootSupportedProperties.INCLUDE_APPLICATION_NAME,
				"false", SpringBootSupportedProperties.INCLUDE_APPLICATION_GROUP, "false")));
		assertFalse(patterns.consolePattern().contains("APPLICATION_NAME"));
		assertFalse(patterns.consolePattern().contains("APPLICATION_GROUP"));
	}

}

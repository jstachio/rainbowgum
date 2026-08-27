package io.jstach.rainbowgum.spring.boot3;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.StandardEnvironment;

import io.jstach.rainbowgum.LogProperties;
import io.jstach.rainbowgum.spring.boot3.RainbowGumLoggingSystemFactory.SpringLogProperties;

class SpringLogPropertiesTest {

	private static SpringLogProperties properties(Map<String, Object> map) {
		var environment = new StandardEnvironment();
		environment.getPropertySources().addFirst(new MapPropertySource("test", map));
		return new SpringLogProperties(environment);
	}

	@Test
	void fileNameTakesPrecedenceOverFilePath() {
		var p = properties(Map.of(LogProperties.FILE_PROPERTY, "explicit.log", SpringBootSupportedProperties.FILE_PATH,
				"/var/log"));
		assertEquals("explicit.log", p.valueOrNull(LogProperties.FILE_PROPERTY));
	}

	@Test
	void filePathSynthesizesSpringLogFilename() {
		var p = properties(Map.of(SpringBootSupportedProperties.FILE_PATH, "/var/log"));
		assertEquals("/var/log/spring.log", p.valueOrNull(LogProperties.FILE_PROPERTY));
	}

	@Test
	void neitherFileNameNorPathMeansNoFile() {
		var p = properties(Map.of());
		assertNull(p.valueOrNull(LogProperties.FILE_PROPERTY));
	}

	@Test
	void ansiNeverMapsToGlobalDisableTrue() {
		var p = properties(Map.of(SpringBootSupportedProperties.OUTPUT_ANSI_ENABLED, "NEVER"));
		assertEquals("true", p.valueOrNull(LogProperties.GLOBAL_ANSI_DISABLE_PROPERTY));
	}

	@Test
	void ansiAlwaysMapsToGlobalDisableFalse() {
		var p = properties(Map.of(SpringBootSupportedProperties.OUTPUT_ANSI_ENABLED, "ALWAYS"));
		assertEquals("false", p.valueOrNull(LogProperties.GLOBAL_ANSI_DISABLE_PROPERTY));
	}

	@Test
	void ansiDetectLeavesAutoDetectionAlone() {
		var p = properties(Map.of(SpringBootSupportedProperties.OUTPUT_ANSI_ENABLED, "DETECT"));
		assertNull(p.valueOrNull(LogProperties.GLOBAL_ANSI_DISABLE_PROPERTY));
	}

	@Test
	void ansiUnsetFallsBackToDirectPropertyIfSet() {
		var p = properties(Map.of(LogProperties.GLOBAL_ANSI_DISABLE_PROPERTY, "true"));
		assertEquals("true", p.valueOrNull(LogProperties.GLOBAL_ANSI_DISABLE_PROPERTY));
	}

	@Test
	void loggingLevelFallsBackToRootLevel() {
		var p = properties(Map.of(SpringBootSupportedProperties.LOGGING_LEVEL_ROOT, "DEBUG"));
		assertEquals("DEBUG", p.valueOrNull(SpringBootSupportedProperties.LOGGING_LEVEL));
	}

}

package io.jstach.rainbowgum.spring.boot4;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.StandardEnvironment;

import io.jstach.rainbowgum.LogAppender;
import io.jstach.rainbowgum.LogProperties;
import io.jstach.rainbowgum.spring.boot4.RainbowGumLoggingSystemFactory.SpringLogProperties;

class SpringLogPropertiesTest {

	private static SpringLogProperties properties(Map<String, Object> map) {
		var environment = new StandardEnvironment();
		environment.getPropertySources().addFirst(new MapPropertySource("test", map));
		return new SpringLogProperties(environment);
	}

	@Test
	void fileNameTakesPrecedenceOverFilePath() {
		var p = properties(Map.of("logging.file.name", "explicit.log", "logging.file.path", "/var/log"));
		assertEquals("explicit.log", p.valueOrNull(LogProperties.FILE_PROPERTY));
	}

	@Test
	void filePathSynthesizesSpringLogFilename() {
		var p = properties(Map.of("logging.file.path", "/var/log"));
		assertEquals("/var/log/spring.log", p.valueOrNull(LogProperties.FILE_PROPERTY));
	}

	@Test
	void neitherFileNameNorPathMeansNoFile() {
		var p = properties(Map.of());
		assertNull(p.valueOrNull(LogProperties.FILE_PROPERTY));
	}

	@Test
	void consoleDisabledWithFileConfiguredRestrictsToFileAppender() {
		var p = properties(Map.of("logging.console.enabled", "false", "logging.file.name", "app.log"));
		assertEquals(LogAppender.FILE_APPENDER_NAME, p.valueOrNull(LogProperties.APPENDERS_PROPERTY));
	}

	@Test
	void consoleDisabledWithNoFileConfiguredDoesNotForceAppenders() {
		var p = properties(Map.of("logging.console.enabled", "false"));
		assertNull(p.valueOrNull(LogProperties.APPENDERS_PROPERTY));
	}

	@Test
	void consoleEnabledDoesNotOverrideAppenders() {
		var p = properties(Map.of("logging.console.enabled", "true", "logging.file.name", "app.log"));
		assertNull(p.valueOrNull(LogProperties.APPENDERS_PROPERTY));
	}

	@Test
	void explicitAppendersPropertyIsNeverOverridden() {
		var p = properties(Map.of("logging.console.enabled", "false", "logging.file.name", "app.log",
				"logging.appenders", "console"));
		assertEquals("console", p.valueOrNull(LogProperties.APPENDERS_PROPERTY));
	}

	@Test
	void ansiNeverMapsToGlobalDisableTrue() {
		var p = properties(Map.of("spring.output.ansi.enabled", "NEVER"));
		assertEquals("true", p.valueOrNull(LogProperties.GLOBAL_ANSI_DISABLE_PROPERTY));
	}

	@Test
	void ansiAlwaysMapsToGlobalDisableFalse() {
		var p = properties(Map.of("spring.output.ansi.enabled", "ALWAYS"));
		assertEquals("false", p.valueOrNull(LogProperties.GLOBAL_ANSI_DISABLE_PROPERTY));
	}

	@Test
	void ansiDetectLeavesAutoDetectionAlone() {
		var p = properties(Map.of("spring.output.ansi.enabled", "DETECT"));
		assertNull(p.valueOrNull(LogProperties.GLOBAL_ANSI_DISABLE_PROPERTY));
	}

	@Test
	void ansiUnsetFallsBackToDirectPropertyIfSet() {
		var p = properties(Map.of("logging.global.ansi.disable", "true"));
		assertEquals("true", p.valueOrNull(LogProperties.GLOBAL_ANSI_DISABLE_PROPERTY));
	}

	@Test
	void loggingLevelFallsBackToRootLevel() {
		var p = properties(Map.of("logging.level.root", "DEBUG"));
		assertEquals("DEBUG", p.valueOrNull("logging.level"));
	}

}

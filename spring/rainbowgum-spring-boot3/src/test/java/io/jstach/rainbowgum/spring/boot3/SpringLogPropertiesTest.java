package io.jstach.rainbowgum.spring.boot3;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.boot.convert.ApplicationConversionService;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.StandardEnvironment;

import io.jstach.rainbowgum.LogProperties;
import io.jstach.rainbowgum.spring.boot3.RainbowGumLoggingSystemFactory.SpringLogProperties;

class SpringLogPropertiesTest {

	private static SpringLogProperties properties(Map<String, Object> map) {
		var environment = new StandardEnvironment();
		// matches what Spring Boot's real bootstrap installs - needed for typed
		// Environment.getProperty(key, DataSize.class)/Charset.class conversions to
		// work the same way production code relies on them to.
		environment.setConversionService(new ApplicationConversionService());
		environment.getPropertySources().addFirst(new MapPropertySource("test", map));
		return new SpringLogProperties(environment);
	}

	@Test
	void fileNameTakesPrecedenceOverFilePath() {
		// absolute input so the expected rolling:/// URI is deterministic regardless of
		// the test's working directory (Path.toUri() resolves relative paths against it)
		var p = properties(Map.of(LogProperties.FILE_PROPERTY, "/tmp/explicit.log",
				SpringBootSupportedProperties.FILE_PATH, "/var/log"));
		assertEquals("rolling:///tmp/explicit.log", p.valueOrNull(LogProperties.FILE_PROPERTY));
	}

	@Test
	void filePathSynthesizesSpringLogFilename() {
		// vanilla Spring Boot/Logback always rolls once file output is configured at
		// all, so FILE_PROPERTY resolves to a rolling:/// URI, not a plain path - see
		// RainbowGumLoggingSystemFactory.SpringLogProperties.rollingUri(String).
		var p = properties(Map.of(SpringBootSupportedProperties.FILE_PATH, "/var/log"));
		assertEquals("rolling:///var/log/spring.log", p.valueOrNull(LogProperties.FILE_PROPERTY));
	}

	@Test
	void neitherFileNameNorPathMeansNoFile() {
		var p = properties(Map.of());
		assertNull(p.valueOrNull(LogProperties.FILE_PROPERTY));
	}

	@Test
	void rollingPolicyMaxFileSizeTranslatesDataSizeToBytes() {
		var p = properties(Map.of(SpringBootSupportedProperties.ROLLINGPOLICY_MAX_FILE_SIZE, "10MB"));
		assertEquals(String.valueOf(10 * 1024 * 1024), p.valueOrNull("logging.output.file.maxFileSize"));
	}

	@Test
	void rollingPolicyTotalSizeCapTranslatesDataSizeToBytes() {
		var p = properties(Map.of(SpringBootSupportedProperties.ROLLINGPOLICY_TOTAL_SIZE_CAP, "100MB"));
		assertEquals(String.valueOf(100L * 1024 * 1024), p.valueOrNull("logging.output.file.totalSizeCap"));
	}

	@Test
	void rollingPolicySizePropertiesAbsentReturnNull() {
		var p = properties(Map.of());
		assertNull(p.valueOrNull("logging.output.file.maxFileSize"));
		assertNull(p.valueOrNull("logging.output.file.totalSizeCap"));
	}

	@Test
	void rollingPolicyMaxHistoryPassesThrough() {
		var p = properties(Map.of(SpringBootSupportedProperties.ROLLINGPOLICY_MAX_HISTORY, "5"));
		assertEquals("5", p.valueOrNull("logging.output.file.maxHistory"));
	}

	@Test
	void rollingPolicyCleanHistoryOnStartPassesThrough() {
		var p = properties(Map.of(SpringBootSupportedProperties.ROLLINGPOLICY_CLEAN_HISTORY_ON_START, "true"));
		assertEquals("true", p.valueOrNull("logging.output.file.cleanHistoryOnStart"));
	}

	@Test
	void rollingPolicyFileNamePatternStripsLogFileToken() {
		var p = properties(Map.of(SpringBootSupportedProperties.ROLLINGPOLICY_FILE_NAME_PATTERN, "${LOG_FILE}.%i.gz"));
		assertEquals(".%i.gz", p.valueOrNull("logging.output.file.fileNamePattern"));
	}

	@Test
	void rollingPolicyFileNamePatternWithDateFallsBackToRainbowGumDefault() {
		// this is Spring Boot's own documented default value for
		// logging.logback.rollingpolicy.file-name-pattern - date based (%d) rotation
		// is not supported, so this must not throw at startup.
		var p = properties(Map.of(SpringBootSupportedProperties.ROLLINGPOLICY_FILE_NAME_PATTERN,
				"${LOG_FILE}.%d{yyyy-MM-dd}.%i.gz"));
		assertNull(p.valueOrNull("logging.output.file.fileNamePattern"));
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

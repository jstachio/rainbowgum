package io.jstach.rainbowgum.rolling;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import io.jstach.rainbowgum.LogConfig;
import io.jstach.rainbowgum.LogProperties;
import io.jstach.rainbowgum.LogProperty.ValidationException;

/*
 * maxFileSize/maxHistory/totalSizeCap/fileNamePattern (all Integer/String properties
 * that can genuinely fail conversion or cross-field validation) had no test exercising
 * a malformed value through the actual property path before this - only through direct
 * calls to the builder's own setters (RollingFileOutputTest) or the pure parser
 * (RollingPolicyTest's ParsedPattern.parse) - found by grepping the property list
 * ConfigProcessor#PROPERTY_LIST_OPTION generates against the test tree. Fast unit tests
 * like RollingFileOutputBuilderTest, not the slow end-to-end rainbowgum-test-file module
 * - all four failures happen before any file is touched.
 */
class RollingFileOutputPropertiesTest {

	@TempDir
	Path dir;

	@Test
	void badMaxFileSizeFailsLoudlyWithPropertyDescription() {
		var e = assertThrows(ValidationException.class, () -> buildWith("maxFileSize", "notanumber"));
		assertEquals(
				"""
						Validation failed for io.jstach.rainbowgum.rolling.RollingFileOutputBuilder:
						Error for property. key: 'logging.output.file.maxFileSize' from PROPERTIES_STRING[logging.output.file.maxFileSize], java.lang.NumberFormatException For input string: "notanumber"
						Tried: 'logging.output.file.maxFileSize' from PROPERTIES_STRING[logging.output.file.maxFileSize]""",
				e.getMessage());
	}

	@Test
	void badMaxHistoryFailsLoudlyWithPropertyDescription() {
		var e = assertThrows(ValidationException.class, () -> buildWith("maxHistory", "notanumber"));
		assertEquals(
				"""
						Validation failed for io.jstach.rainbowgum.rolling.RollingFileOutputBuilder:
						Error for property. key: 'logging.output.file.maxHistory' from PROPERTIES_STRING[logging.output.file.maxHistory], java.lang.NumberFormatException For input string: "notanumber"
						Tried: 'logging.output.file.maxHistory' from PROPERTIES_STRING[logging.output.file.maxHistory]""",
				e.getMessage());
	}

	@Test
	void badTotalSizeCapFailsLoudlyWithPropertyDescription() {
		var e = assertThrows(ValidationException.class, () -> buildWith("totalSizeCap", "notanumber"));
		assertEquals(
				"""
						Validation failed for io.jstach.rainbowgum.rolling.RollingFileOutputBuilder:
						Error for property. key: 'logging.output.file.totalSizeCap' from PROPERTIES_STRING[logging.output.file.totalSizeCap], java.lang.NumberFormatException For input string: "notanumber"
						Tried: 'logging.output.file.totalSizeCap' from PROPERTIES_STRING[logging.output.file.totalSizeCap]""",
				e.getMessage());
	}

	/*
	 * Unlike the three Integer properties above (which fail during fromProperties()'s own
	 * batched Validator - the string itself never fails to parse as a String),
	 * fileNamePattern's content is only validated later, inside build()'s call to the
	 * factory method (RollingPolicy.ParsedPattern.parse) - so this goes through build()'s
	 * own single-cause ValidationException.of(...) instead, same shape as
	 * RollingFileOutputBuilderTest's fileName/uri cross-field check.
	 */
	@Test
	void badFileNamePatternFailsLoudlyWithPropertyDescription() {
		var e = assertThrows(ValidationException.class, () -> buildWith("fileNamePattern", "archive"));
		assertEquals("Validation failed for io.jstach.rainbowgum.rolling.RollingFileOutputBuilder: "
				+ "fileNamePattern must contain %i (rotation index): archive", e.getMessage());
	}

	private void buildWith(String property, String value) {
		String active = dir.resolve("app.log").toString();
		var properties = LogProperties.builder()
			.fromProperties(
					"logging.output.file.fileName=%s\nlogging.output.file.%s=%s".formatted(active, property, value))
			.build();
		var config = LogConfig.builder().properties(properties).build();
		RollingFileOutput.of(b -> {
		}).provide("file", config);
	}

}

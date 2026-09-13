package io.jstach.rainbowgum.rolling;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import io.jstach.rainbowgum.LogConfig;
import io.jstach.rainbowgum.LogProperty.ValidationException;

/*
 * fileName/uri validation happens synchronously in RollingFileOutput.of(...)'s static
 * factory before any file is touched, so this can be a fast unit test here rather than
 * living in the slow end-to-end rainbowgum-test-file module (mirrors FileOutputTest's
 * usingBuilderfileNameAndUriBothNOTSetShouldFail for the plain, non-rolling builder).
 * uri()/toString() below still open a real (empty) file on construction - that part
 * can't be avoided - but need no actual writing/rolling, so they stay here too rather
 * than in the slow module.
 */
class RollingFileOutputBuilderTest {

	@TempDir
	Path dir;

	@Test
	void bothFileNameAndUriUnsetThrowsBeforeAnyFileAccess() {
		var config = LogConfig.builder().build();
		// generated RollingFileOutputBuilder.build() catches the factory method's own
		// cross-field check and rethrows via LogProperty.ValidationException.of(...), not
		// just single-field conversion failures - RollingFileOutput.of(...)'s raw
		// IllegalArgumentException is the cause.
		var e = assertThrows(ValidationException.class, () -> RollingFileOutput.of(b -> {
			b.fileName(null);
			b.uri(null);
		}).provide("fail", config));
		assertEquals("Validation failed for io.jstach.rainbowgum.rolling.RollingFileOutputBuilder: "
				+ "fileName and uri cannot both be unset.", e.getMessage());
		assertInstanceOf(IllegalArgumentException.class, e.getCause());
	}

	@Test
	void uriAndToStringDelegateToActiveFile() throws Exception {
		Path active = dir.resolve("app.log");
		var config = LogConfig.builder().build();
		var output = RollingFileOutput.of(b -> b.fileName(active.toString())).provide("file", config);
		try {
			assertEquals(new File(active.toString()).toURI(), output.uri());
			assertTrue(output.toString().contains(active.toString()),
					() -> "expected toString to mention the active file, got: " + output);
		}
		finally {
			output.close();
		}
	}

}

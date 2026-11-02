package io.jstach.rainbowgum;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

/*
 * The URI chose the component, so a query parameter it does not read is a mistake, such
 * as rolling options on a URI that picked the plain file output.
 */
class UriQueryUnusedKeyTest {

	private static RainbowGum gum(String uri) {
		var config = LogConfig.builder()
			.properties(LogProperties.builder().fromProperties("logging.appender.console.output.uri=" + uri).build())
			.alertsFail(LogAlerts.FailLevel.WARNING)
			.build();
		return RainbowGum.builder(config).build();
	}

	@Test
	void unreadQueryParameterIsAWarning() {
		var e = assertThrows(IllegalStateException.class,
				() -> gum("stdout:///?stream=follow&maxFileSize=10MB").start());
		assertEquals("""
				1 alert(s) at warning or above were recorded while starting and logging.alerts.fail=warning:
				[WARNING] Property key 'logging.appender.console.output.stdout.maxFileSize' from \
				[logging.appender.console.output.uri]->URI(stdout:///?stream=follow&maxFileSize=10MB)[maxFileSize] \
				was set but not read during startup.""", e.getMessage());
	}

	@Test
	void readQueryParametersAreNotReported() {
		try (var gum = gum("stdout:///?stream=follow").start()) {
			assertEquals("[]", gum.config().alerts().dump().toString());
		}
	}

}

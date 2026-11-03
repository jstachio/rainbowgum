package io.jstach.rainbowgum;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.net.URI;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

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
	void defaultStartupFailureRedactsCredentialsInNestedEndpointUris() {
		var config = LogConfig.builder()
			.properties(LogProperties.builder()
				.fromProperties(
						"logging.appender.console.output.uri=stdout:///?endpoint=http%3A%2F%2Falice%3Ademo-password-42%40localhost%3A4318%2Fv1%2Flogs")
				.build())
			.build();
		try (var gum = RainbowGum.builder(config).build()) {
			var failure = assertThrows(IllegalStateException.class, gum::start);
			assertEquals(
					"""
							1 configuration alert(s) at warning or above were recorded while starting and logging.alerts.fail=config (the default):
							[WARNING] Property key 'logging.appender.console.output.stdout.endpoint' from \
							[logging.appender.console.output.uri]->URI(stdout:///?endpoint=<REDACTED>)[endpoint] \
							was set but not read during startup.""",
					failure.getMessage());
		}
	}

	@Test
	void readQueryParametersAreNotReported() {
		try (var gum = gum("stdout:///?stream=follow").start()) {
			assertEquals("[]", gum.config().alerts().dump().toString());
		}
	}

	/*
	 * System properties are always listed, so a key both there and in the query is found
	 * first as a property.
	 */
	@Test
	void unreadQueryParameterIsAWarningWhenAPropertySetsItToo() {
		System.setProperty("logging.appender.console.output.stdout.maxFileSize", "10MB");
		try {
			var e = assertThrows(IllegalStateException.class,
					() -> gum("stdout:///?stream=follow&maxFileSize=10MB").start());
			assertEquals("""
					1 alert(s) at warning or above were recorded while starting and logging.alerts.fail=warning:
					[WARNING] Property key 'logging.appender.console.output.stdout.maxFileSize' from \
					[logging.appender.console.output.uri]->URI(stdout:///?stream=follow&maxFileSize=10MB)[maxFileSize] \
					was set but not read during startup.""", e.getMessage());
		}
		finally {
			System.clearProperty("logging.appender.console.output.stdout.maxFileSize");
		}
	}

	/*
	 * A map is read from the query layer itself, through either form.
	 */
	@ParameterizedTest
	@ValueSource(strings = { "headers.x=y", "headers.keys=x&headers.x=y" })
	void readMapQueryParametersAreNotReported(String query) {
		var check = new UnusedKeyCheck(List.of());
		var config = check.wrap(LogProperties.builder().fromProperties("").build());
		var properties = LogProperties.of(URI.create("stdout:///?" + query), "logging.appender.console.output.stdout.",
				config, "logging.appender.console.output.uri");
		var headers = properties.forKey("logging.appender.console.output.stdout.headers")
			.ofMap()
			.validateNow(UriQueryUnusedKeyTest.class);
		assertEquals(Map.of("x", "y"), headers);
		assertEquals(List.of(), check.unused());
	}

}

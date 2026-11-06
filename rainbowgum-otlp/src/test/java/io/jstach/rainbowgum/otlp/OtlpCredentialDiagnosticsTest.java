package io.jstach.rainbowgum.otlp;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.lang.System.Logger.Level;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Isolated;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import io.jstach.rainbowgum.KeyValues;
import io.jstach.rainbowgum.LogConfig;
import io.jstach.rainbowgum.LogEvent;
import io.jstach.rainbowgum.LogProperties;
import io.jstach.rainbowgum.LogOutput.ContentType.StandardContentType;

/* Changes stderr and the test environment source; no request leaves the process. */
@Isolated
class OtlpCredentialDiagnosticsTest {

	private static final String SECRET = "fake-otlp-credential";

	@AfterEach
	void restoreEnvironment() {
		OtlpEnvironment.source = System::getenv;
	}

	@ParameterizedTest
	@ValueSource(strings = { "Authorization", "X-Honeycomb-Team", "bad-header\n" + SECRET, "Connection" })
	void invalidHeadersFailStartupWithoutValues(String name) {
		var failure = assertThrows(RuntimeException.class,
				() -> OtlpOutput
					.of(b -> b.endpoint(URI.create("http://127.0.0.1:1/v1/logs"))
						.headers(Map.of(name, "Bearer " + SECRET + "\n")))
					.provide("audit", emptyConfig()));
		String trace = trace(failure);
		assertFalse(trace.contains(SECRET), trace);
		String message = String.valueOf(failure.getMessage());
		String expected = switch (name) {
			case "Connection" -> "OTLP header 'Connection' is not allowed";
			case "Authorization", "X-Honeycomb-Team" -> "OTLP header '" + name + "' has an invalid value";
			default -> "OTLP header name is invalid";
		};
		assertTrue(message.contains(expected), message);
	}

	@ParameterizedTest
	@ValueSource(strings = { "OTEL_EXPORTER_OTLP_HEADERS", "OTEL_EXPORTER_OTLP_LOGS_HEADERS" })
	void invalidEnvironmentHeaderValuesFailStartupWithoutValues(String variable) {
		OtlpEnvironment.source = Map.of(variable, "Authorization=Bearer%0A" + SECRET)::get;
		var failure = assertThrows(RuntimeException.class,
				() -> OtlpOutput.of(b -> b.environmentVariables(EnvironmentVariables.ON))
					.provide("audit", emptyConfig()));
		String trace = trace(failure);
		assertFalse(trace.contains(SECRET), trace);
		String message = String.valueOf(failure.getMessage());
		assertTrue(message.contains("OTLP header 'Authorization' has an invalid value"), message);
	}

	/*
	 * Sending never quotes request headers, so an authenticated endpoint keeps the
	 * connection error.
	 */
	@Test
	void sendFailuresWithHeadersKeepDetails() {
		var original = System.err;
		var bytes = new ByteArrayOutputStream();
		var config = emptyConfig();
		try (var capture = new PrintStream(bytes, true, StandardCharsets.UTF_8)) {
			System.setErr(capture);
			try (var output = OtlpOutput
				.of(b -> b.endpoint(URI.create("http://127.0.0.1:1/v1/logs"))
					.headers(Map.of("Authorization", "Bearer " + SECRET)))
				.provide("audit", config)) {
				output.write(LogEvent.of(Instant.EPOCH, "main", 1, Level.INFO, "audit", "ordinary message",
						KeyValues.of(), null), new byte[0], 0, 0, StandardContentType.APPLICATION_JSON);
			}
		}
		finally {
			System.setErr(original);
		}
		String stderr = bytes.toString(StandardCharsets.UTF_8);
		assertFalse(stderr.contains(SECRET), stderr);
		assertEquals(1, config.alerts().dump().size());
		var cause = config.alerts().dump().getFirst().throwableOrNull();
		assertNotNull(cause);
		assertFalse(trace(cause).contains(SECRET), trace(cause));
		assertFalse(String.valueOf(cause.getMessage()).contains("credential details omitted"), trace(cause));
	}

	@ParameterizedTest
	@ValueSource(strings = { "OTEL_EXPORTER_OTLP_HEADERS", "OTEL_EXPORTER_OTLP_LOGS_HEADERS" })
	void malformedEnvironmentHeaderErrorsOmitValues(String variable) {
		for (String value : List.of("Authorization:Bearer " + SECRET, "=Bearer " + SECRET, "X-Team=ordinary,," + SECRET,
				"Authorization=Bearer%ZZ" + SECRET, "Authorization=Bearer%")) {
			OtlpEnvironment.source = Map.of(variable, value)::get;
			var failure = assertThrows(RuntimeException.class,
					() -> OtlpOutput.of(b -> b.environmentVariables(EnvironmentVariables.ON))
						.provide("audit", emptyConfig()));
			String trace = trace(failure);
			assertFalse(trace.contains(SECRET), trace);
			String message = String.valueOf(failure.getMessage());
			assertTrue(message.contains(variable + " entry "), message);
			assertTrue(message.contains("Expected key=value") || message.contains("Invalid percent-encoding"), message);
		}
	}

	@ParameterizedTest
	@ValueSource(strings = { "OTEL_EXPORTER_OTLP_ENDPOINT", "OTEL_EXPORTER_OTLP_LOGS_ENDPOINT" })
	void malformedEnvironmentEndpointsOmitCredentials(String variable) {
		OtlpEnvironment.source = Map.of(variable,
				"http://alice:" + SECRET + "@127.0.0.1/invalid path?token=" + SECRET)::get;
		var failure = assertThrows(RuntimeException.class,
				() -> OtlpOutput.of(b -> b.environmentVariables(EnvironmentVariables.ON))
					.provide("audit", emptyConfig()));
		String trace = trace(failure);
		assertFalse(trace.contains(SECRET), trace);
		String message = String.valueOf(failure.getMessage());
		assertTrue(message.contains(variable), message);
		assertTrue(message.contains("Invalid URI: Illegal character in path at index "), message);
	}

	private static LogConfig emptyConfig() {
		return LogConfig.builder().properties(LogProperties.StandardProperties.EMPTY).build();
	}

	private static String trace(Throwable throwable) {
		var text = new StringWriter();
		throwable.printStackTrace(new PrintWriter(text));
		return text.toString();
	}

}

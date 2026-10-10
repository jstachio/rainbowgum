package io.jstach.rainbowgum.otlp;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.net.URI;
import java.util.Map;
import java.util.function.Function;

import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Isolated;

import io.jstach.rainbowgum.LogConfig;
import io.jstach.rainbowgum.LogProperties;
import io.jstach.rainbowgum.LogProperty;
import io.jstach.rainbowgum.LogProviderRef;

/*
 * OtlpEnvironment.source is static, so these tests run isolated.
 */
@Isolated
class OtlpConfigTest {

	@AfterEach
	void restoreEnvironment() {
		OtlpEnvironment.source = System::getenv;
	}

	static void env(Map<String, String> vars) {
		Function<String, @Nullable String> source = vars::get;
		OtlpEnvironment.source = source;
	}

	static OtlpOutput output(String properties) {
		var config = LogConfig.builder()
			.properties(LogProperties.builder().fromProperties(properties).build())
			.configurator(new OtlpConfigurator())
			.build();
		var output = (OtlpOutput) config.outputRegistry()
			.provide(LogProviderRef.of(URI.create("otlp:///")))
			.provide("otlp", config);
		output.close();
		return output;
	}

	@Test
	void environmentIsIgnoredUnlessEnabled() {
		env(Map.of("OTEL_EXPORTER_OTLP_ENDPOINT", "http://collector:4318", "OTEL_EXPORTER_OTLP_PROTOCOL", "http/json"));
		var output = output("");
		assertEquals(URI.create("http://localhost:4318/v1/logs"), output.endpoint());
		assertEquals(OtlpProtocol.HTTP_PROTOBUF, output.protocol());
	}

	@Test
	void genericEndpointGetsLogsPathAppended() {
		env(Map.of("OTEL_EXPORTER_OTLP_ENDPOINT", "http://collector:4318/", "OTEL_EXPORTER_OTLP_PROTOCOL",
				"http/json"));
		var output = output("logging.output.otlp.otlp.environmentVariables=on");
		assertEquals(URI.create("http://collector:4318/v1/logs"), output.endpoint());
		assertEquals(OtlpProtocol.HTTP_JSON, output.protocol());
	}

	@Test
	void logsEndpointIsUsedAsIsAndPropertiesWin() {
		env(Map.of("OTEL_EXPORTER_OTLP_LOGS_ENDPOINT", "http://collector:4318/custom", "OTEL_EXPORTER_OTLP_ENDPOINT",
				"http://ignored:4318", "OTEL_EXPORTER_OTLP_LOGS_PROTOCOL", "http/json"));
		assertEquals(URI.create("http://collector:4318/custom"),
				output("logging.output.otlp.otlp.environmentVariables=true").endpoint());
		var explicit = output("""
				logging.output.otlp.otlp.environmentVariables=on
				logging.output.otlp.otlp.endpoint=http://explicit:9999/v1/logs
				logging.output.otlp.otlp.protocol=http/protobuf
				""");
		assertEquals(URI.create("http://explicit:9999/v1/logs"), explicit.endpoint());
		assertEquals(OtlpProtocol.HTTP_PROTOBUF, explicit.protocol());
	}

	@Test
	void resourcePrecedence() {
		env(Map.of("OTEL_RESOURCE_ATTRIBUTES", "service.name=fromAttributes,team=a%2Cb", "OTEL_SERVICE_NAME",
				"fromEnv"));
		assertEquals(Map.of("service.name", "unknown_service:java"),
				OtlpResource.of(null, null, EnvironmentVariables.OFF).attributes());
		assertEquals(Map.of("service.name", "fromEnv", "team", "a,b"),
				OtlpResource.of(null, null, EnvironmentVariables.ON).attributes());
		assertEquals(Map.of("service.name", "explicit", "team", "override"),
				OtlpResource.of("explicit", Map.of("team", "override"), EnvironmentVariables.ON).attributes());
	}

	@Test
	void invalidValuesFailWithPropertyContext() {
		var e = assertThrows(LogProperty.ValidationException.class,
				() -> output("logging.output.otlp.otlp.environmentVariables=maybe"));
		assertEquals(
				"""
						Validation failed for io.jstach.rainbowgum.otlp.OtlpOutputBuilder:
						Error for property. key: 'logging.output.otlp.otlp.environmentVariables' from PROPERTIES_STRING[logging.output.otlp.otlp.environmentVariables], 'maybe' is not a valid value for io.jstach.rainbowgum.otlp.EnvironmentVariables. Valid values: 'off', 'on', 'true', 'false'
						Tried:
						    'logging.output.otlp.otlp.environmentVariables' from:
						        PROPERTIES_STRING[logging.output.otlp.otlp.environmentVariables],
						        URI(otlp:///)[environmentVariables]""",
				e.getMessage());
		assertEquals("OTLP protocol 'grpc' is not supported. Use http/protobuf or http/json.",
				assertThrows(IllegalArgumentException.class, () -> OtlpProtocol.parse("grpc")).getMessage());
		assertEquals("Invalid OTEL_RESOURCE_ATTRIBUTES entry: 'novalue'. Expected key=value.",
				assertThrows(IllegalArgumentException.class,
						() -> OtlpEnvironment.parseKeyValues("a=b,novalue", "OTEL_RESOURCE_ATTRIBUTES"))
					.getMessage());
	}

}

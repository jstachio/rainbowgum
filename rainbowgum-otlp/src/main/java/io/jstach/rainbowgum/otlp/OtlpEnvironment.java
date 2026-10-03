package io.jstach.rainbowgum.otlp;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Function;

import org.jspecify.annotations.Nullable;

/*
 * Reads the standard OpenTelemetry environment variables. Only consulted when
 * EnvironmentVariables.ON was configured.
 */
final class OtlpEnvironment {

	// Replaced by tests, which cannot set real environment variables.
	static Function<String, @Nullable String> source = System::getenv;

	static final String DEFAULT_ENDPOINT = "http://localhost:4318/v1/logs";

	private OtlpEnvironment() {
	}

	static @Nullable String get(String name) {
		var v = source.apply(name);
		return v == null || v.isBlank() ? null : v.strip();
	}

	/*
	 * The logs specific variable is used as is; the generic one gets /v1/logs appended,
	 * as the OTLP exporter spec requires.
	 */
	static @Nullable String logsEndpoint() {
		var logs = get("OTEL_EXPORTER_OTLP_LOGS_ENDPOINT");
		if (logs != null) {
			return logs;
		}
		var generic = get("OTEL_EXPORTER_OTLP_ENDPOINT");
		if (generic == null) {
			return null;
		}
		return generic.endsWith("/") ? generic + "v1/logs" : generic + "/v1/logs";
	}

	static @Nullable String logsValue(String suffix) {
		var logs = get("OTEL_EXPORTER_OTLP_LOGS_" + suffix);
		return logs != null ? logs : get("OTEL_EXPORTER_OTLP_" + suffix);
	}

	static Map<String, String> headers() {
		var all = new LinkedHashMap<String, String>();
		all.putAll(parseKeyValues(get("OTEL_EXPORTER_OTLP_HEADERS"), "OTEL_EXPORTER_OTLP_HEADERS"));
		all.putAll(parseKeyValues(get("OTEL_EXPORTER_OTLP_LOGS_HEADERS"), "OTEL_EXPORTER_OTLP_LOGS_HEADERS"));
		return all;
	}

	/*
	 * OTEL_SERVICE_NAME wins over a service.name inside OTEL_RESOURCE_ATTRIBUTES.
	 */
	static Map<String, String> resourceAttributes() {
		var attributes = new LinkedHashMap<String, String>(
				parseKeyValues(get("OTEL_RESOURCE_ATTRIBUTES"), "OTEL_RESOURCE_ATTRIBUTES"));
		var serviceName = get("OTEL_SERVICE_NAME");
		if (serviceName != null) {
			attributes.put(OtlpResource.SERVICE_NAME, serviceName);
		}
		return attributes;
	}

	/*
	 * W3C Baggage style list used by OTEL_RESOURCE_ATTRIBUTES and the headers variables:
	 * comma separated key=value pairs with percent encoded values.
	 */
	static Map<String, String> parseKeyValues(@Nullable String value, String name) {
		var result = new LinkedHashMap<String, String>();
		if (value == null) {
			return result;
		}
		for (String pair : value.split(",", -1)) {
			if (pair.isBlank()) {
				continue;
			}
			int eq = pair.indexOf('=');
			if (eq <= 0) {
				throw new IllegalArgumentException(
						"Invalid " + name + " entry: '" + pair.strip() + "'. Expected key=value.");
			}
			String key = pair.substring(0, eq).strip();
			String v = pair.substring(eq + 1).strip();
			result.put(key, URLDecoder.decode(v.replace("+", "%2B"), StandardCharsets.UTF_8));
		}
		return result;
	}

}

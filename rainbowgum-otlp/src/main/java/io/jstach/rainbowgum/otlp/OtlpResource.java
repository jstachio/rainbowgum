package io.jstach.rainbowgum.otlp;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

import org.jspecify.annotations.Nullable;

/*
 * OTLP Resource attributes. Precedence, highest first: explicit serviceName, explicit
 * resourceAttributes, environment variables (only when enabled), then the spec's
 * default service.name.
 */
record OtlpResource(Map<String, String> attributes) {

	static final String SERVICE_NAME = "service.name";

	static final String DEFAULT_SERVICE_NAME = "unknown_service:java";

	// Insertion order is kept (service.name first) so output is deterministic.
	OtlpResource {
		attributes = Collections.unmodifiableMap(new LinkedHashMap<>(attributes));
	}

	static OtlpResource of(@Nullable String serviceName, @Nullable Map<String, String> resourceAttributes,
			EnvironmentVariables environmentVariables) {
		var attributes = new LinkedHashMap<String, String>();
		attributes.put(SERVICE_NAME, DEFAULT_SERVICE_NAME);
		if (environmentVariables == EnvironmentVariables.ON) {
			attributes.putAll(OtlpEnvironment.resourceAttributes());
		}
		if (resourceAttributes != null) {
			attributes.putAll(resourceAttributes);
		}
		if (serviceName != null) {
			attributes.put(SERVICE_NAME, serviceName);
		}
		return new OtlpResource(attributes);
	}

}

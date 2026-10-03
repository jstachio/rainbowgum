package io.jstach.rainbowgum.otlp;

import java.util.Locale;

/**
 * Whether the standard OpenTelemetry environment variables ({@code OTEL_SERVICE_NAME},
 * {@code OTEL_RESOURCE_ATTRIBUTES}, and {@code OTEL_EXPORTER_OTLP_*}) are read. Off by
 * default: reading the process environment has to be enabled explicitly. Properties set
 * directly always take precedence over environment variables.
 */
public enum EnvironmentVariables {

	/**
	 * Environment variables are ignored (the default).
	 */
	OFF,
	/**
	 * Environment variables are read for any value not set by a property.
	 */
	ON;

	/**
	 * Parses a property value: {@code on}/{@code true} or {@code off}/{@code false}, case
	 * insensitive.
	 * @param value property value.
	 * @return parsed value.
	 * @throws IllegalArgumentException if the value is not recognized.
	 */
	public static EnvironmentVariables parse(String value) {
		return switch (value.strip().toLowerCase(Locale.ROOT)) {
			case "on", "true" -> ON;
			case "off", "false" -> OFF;
			default -> throw new IllegalArgumentException(
					"Invalid environmentVariables value: '" + value + "'. Expected one of: on, off, true, false.");
		};
	}

}

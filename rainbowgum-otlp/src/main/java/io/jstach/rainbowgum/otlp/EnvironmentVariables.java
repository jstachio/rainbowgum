package io.jstach.rainbowgum.otlp;

import java.util.Locale;

import io.jstach.rainbowgum.LogProperty;
import io.jstach.rainbowgum.annotation.EnumAlias;

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
	@EnumAlias("false")
	OFF,
	/**
	 * Environment variables are read for any value not set by a property.
	 */
	@EnumAlias("true")
	ON;

	/**
	 * Parses a property value: {@code on}/{@code true} or {@code off}/{@code false}, case
	 * insensitive.
	 * @param value property value.
	 * @return parsed value.
	 * @throws IllegalArgumentException if the value is not recognized.
	 */
	public static EnvironmentVariables parse(String value) {
		String v = value.strip();
		return switch (v.toLowerCase(Locale.ROOT)) {
			case "true" -> ON;
			case "false" -> OFF;
			default -> LogProperty.enumValue(EnvironmentVariables.class, v, "true", "false");
		};
	}

}

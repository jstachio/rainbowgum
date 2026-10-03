package io.jstach.rainbowgum.otlp;

import java.util.Locale;

/**
 * OTLP/HTTP payload encoding, named as in {@code OTEL_EXPORTER_OTLP_PROTOCOL}.
 */
public enum OtlpProtocol {

	/**
	 * Binary protobuf, {@code application/x-protobuf} (the OTLP default).
	 */
	HTTP_PROTOBUF("http/protobuf", "application/x-protobuf"),
	/**
	 * JSON, {@code application/json}.
	 */
	HTTP_JSON("http/json", "application/json");

	private final String label;

	private final String contentType;

	OtlpProtocol(String label, String contentType) {
		this.label = label;
		this.contentType = contentType;
	}

	/**
	 * The protocol name as used by {@code OTEL_EXPORTER_OTLP_PROTOCOL}.
	 * @return protocol name.
	 */
	public String label() {
		return label;
	}

	/**
	 * The HTTP content type of the request body.
	 * @return content type.
	 */
	public String contentType() {
		return contentType;
	}

	/**
	 * Parses {@code http/protobuf} or {@code http/json}, case insensitive.
	 * @param value protocol name.
	 * @return protocol.
	 * @throws IllegalArgumentException if the value is not a supported protocol.
	 */
	public static OtlpProtocol parse(String value) {
		return switch (value.strip().toLowerCase(Locale.ROOT)) {
			case "http/protobuf" -> HTTP_PROTOBUF;
			case "http/json" -> HTTP_JSON;
			case "grpc" -> throw new IllegalArgumentException(
					"OTLP protocol 'grpc' is not supported. Use http/protobuf or http/json.");
			default -> throw new IllegalArgumentException(
					"Invalid OTLP protocol: '" + value + "'. Expected one of: http/protobuf, http/json.");
		};
	}

}

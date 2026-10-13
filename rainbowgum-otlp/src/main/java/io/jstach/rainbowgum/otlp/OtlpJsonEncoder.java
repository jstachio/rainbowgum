package io.jstach.rainbowgum.otlp;

import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

import org.jspecify.annotations.Nullable;

import io.jstach.rainbowgum.LogEncoder;
import io.jstach.rainbowgum.LogEvent;
import io.jstach.rainbowgum.LogProperties;
import io.jstach.rainbowgum.LogProvider;
import io.jstach.rainbowgum.annotation.LogConfigurable;
import io.jstach.rainbowgum.annotation.LogConfigurable.ConvertParameter;
import io.jstach.rainbowgum.json.JsonBuffer;
import io.jstach.rainbowgum.json.JsonBuffer.ExtendedFieldPrefix;

/**
 * Encodes each event as one line of OTLP/JSON: a complete {@code LogsData} object holding
 * a single log record. This is the OpenTelemetry file format, read by the OpenTelemetry
 * Collector's {@code otlpjsonfile} receiver or by the {@code filelog} receiver with the
 * {@code otlpjson} connector, so it needs no network connection.
 * <p>
 * Each record carries the formatted message as its body, the logger name as its
 * instrumentation scope, {@code thread.name}, {@code thread.id}, the key values selected
 * by {@code keyValuesIncluded} and {@code keyValuesExcluded} (all by default), and for a
 * throwable {@code exception.type}, {@code exception.message}, and
 * {@code exception.stacktrace} attributes. When the event has caller info it also carries
 * {@code code.function.name}, {@code code.file.path}, and {@code code.line.number}. Event
 * thread, caller, and exception attributes take precedence over key values with the same
 * names. Key values named by {@code traceIdKey} and {@code spanIdKey} (default
 * {@code traceId} and {@code spanId}, as Micrometer Tracing puts them in the MDC) become
 * the record's trace context when they are valid W3C ids, and the key value
 * {@code otel.event.name} becomes the record's event name.
 * <p>
 * The include and exclude lists are case sensitive globs where {@code *} matches any
 * characters and {@code ?} matches one character. An excluded key is never added.
 */
public final class OtlpJsonEncoder extends LogEncoder.AbstractEncoder<JsonBuffer> {

	/**
	 * Encoder URI scheme.
	 */
	public static final String OTLP_SCHEME = "otlp";

	/**
	 * Default key value name holding the trace id.
	 */
	public static final String DEFAULT_TRACE_ID_KEY = "traceId";

	/**
	 * Default key value name holding the span id.
	 */
	public static final String DEFAULT_SPAN_ID_KEY = "spanId";

	private final OtlpResource resource;

	private final OtlpRecord.Mapping mapping;

	OtlpJsonEncoder(OtlpResource resource, OtlpRecord.Mapping mapping) {
		this.resource = resource;
		this.mapping = mapping;
	}

	/**
	 * Creates the encoder with a builder lambda; properties are applied after the lambda.
	 * @param consumer configures the builder.
	 * @return encoder provider.
	 */
	public static LogProvider<OtlpJsonEncoder> of(Consumer<OtlpJsonEncoderBuilder> consumer) {
		return (s, c) -> {
			var b = new OtlpJsonEncoderBuilder(s);
			consumer.accept(b);
			return b.fromProperties(c.properties()).build();
		};
	}

	/**
	 * Creates the encoder.
	 * @param name property name prefix.
	 * @param serviceName {@code service.name} resource attribute, default
	 * {@code unknown_service:java}.
	 * @param resourceAttributes additional resource attributes.
	 * @param traceIdKey key value holding the trace id, default {@code traceId}.
	 * @param spanIdKey key value holding the span id, default {@code spanId}.
	 * @param keyValuesIncluded globs of key values to add as attributes, default all.
	 * @param keyValuesExcluded globs of key values not to add as attributes.
	 * @param environmentVariables whether {@code OTEL_SERVICE_NAME} and
	 * {@code OTEL_RESOURCE_ATTRIBUTES} are read, default off.
	 * @return encoder.
	 */
	@LogConfigurable(name = "OtlpJsonEncoderBuilder", prefix = LogProperties.ENCODER_PREFIX + "otlp.")
	static OtlpJsonEncoder of(@LogConfigurable.KeyParameter String name, @Nullable String serviceName,
			@Nullable Map<String, String> resourceAttributes, @Nullable String traceIdKey, @Nullable String spanIdKey,
			@Nullable List<String> keyValuesIncluded, @Nullable List<String> keyValuesExcluded,
			@ConvertParameter("parseEnvironmentVariables") @Nullable EnvironmentVariables environmentVariables) {
		var env = environmentVariables == null ? EnvironmentVariables.OFF : environmentVariables;
		return new OtlpJsonEncoder(OtlpResource.of(serviceName, resourceAttributes, env),
				OtlpRecord.Mapping.of(traceIdKey, spanIdKey, keyValuesIncluded, keyValuesExcluded));
	}

	static EnvironmentVariables parseEnvironmentVariables(String value) {
		return EnvironmentVariables.parse(value);
	}

	@Override
	protected JsonBuffer doBuffer(BufferHints hints) {
		return new JsonBuffer(false, ExtendedFieldPrefix.AT, -1);
	}

	@Override
	protected void doEncode(LogEvent event, JsonBuffer buffer) {
		buffer.clear();
		OtlpJson.write(buffer, resource, List.of(OtlpRecord.of(event, mapping)));
		buffer.writeLineFeed();
	}

}

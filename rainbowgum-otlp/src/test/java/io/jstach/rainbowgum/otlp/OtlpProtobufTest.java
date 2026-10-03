package io.jstach.rainbowgum.otlp;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.System.Logger.Level;
import java.time.Instant;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;

import io.jstach.rainbowgum.KeyValues;
import io.jstach.rainbowgum.LogEvent;
import io.opentelemetry.proto.collector.logs.v1.ExportLogsServiceRequest;
import io.opentelemetry.proto.common.v1.AnyValue;
import io.opentelemetry.proto.common.v1.KeyValue;
import io.opentelemetry.proto.logs.v1.LogRecord;
import io.opentelemetry.proto.logs.v1.SeverityNumber;

/*
 * Decodes the hand written protobuf with the official generated OTLP classes, so a wrong
 * field number or wire type fails here.
 */
class OtlpProtobufTest {

	static final String TRACE_ID = "4BF92F3577B34DA6A3CE929D0E0E4736";

	static final String SPAN_ID = "00f067aa0ba902b7";

	static LogEvent event(String logger, Level level, String message, Map<String, String> kvs,
			@Nullable Throwable throwable) {
		return LogEvent.of(Instant.ofEpochSecond(1_700_000_000L, 123_456_789), "main", 42, level, logger, message,
				KeyValues.of(kvs), throwable);
	}

	static class NoStackException extends RuntimeException {

		private static final long serialVersionUID = 1L;

		NoStackException(String message) {
			super(message, null, false, false);
		}

	}

	@Test
	void decodesWithOfficialClasses() throws Exception {
		var kvs = new LinkedHashMap<String, String>();
		kvs.put("traceId", TRACE_ID);
		kvs.put("spanId", SPAN_ID);
		kvs.put("requestId", "abc-123");
		var first = event("com.example.A", Level.ERROR, "boom", kvs, new NoStackException("bad"));
		var second = event("com.example.B", Level.WARNING, "careful", Map.of(), null);
		var resource = OtlpResource.of("orders", Map.of("deployment.environment", "prod"), EnvironmentVariables.OFF);
		var records = List.of(OtlpRecord.of(first, "traceId", "spanId"), OtlpRecord.of(second, "traceId", "spanId"));

		var request = ExportLogsServiceRequest.parseFrom(OtlpProtobuf.encode(resource, records));

		assertEquals(1, request.getResourceLogsCount());
		var resourceLogs = request.getResourceLogs(0);
		assertEquals(Map.of("service.name", "orders", "deployment.environment", "prod"),
				strings(resourceLogs.getResource().getAttributesList()));
		assertEquals(List.of("com.example.A", "com.example.B"),
				resourceLogs.getScopeLogsList().stream().map(s -> s.getScope().getName()).toList());

		LogRecord a = resourceLogs.getScopeLogs(0).getLogRecords(0);
		assertEquals(1_700_000_000_123_456_789L, a.getTimeUnixNano());
		assertEquals(1_700_000_000_123_456_789L, a.getObservedTimeUnixNano());
		assertEquals(SeverityNumber.SEVERITY_NUMBER_ERROR, a.getSeverityNumber());
		assertEquals("ERROR", a.getSeverityText());
		assertEquals("boom", a.getBody().getStringValue());
		assertEquals(TRACE_ID.toLowerCase(java.util.Locale.ROOT),
				HexFormat.of().formatHex(a.getTraceId().toByteArray()));
		assertEquals(SPAN_ID, HexFormat.of().formatHex(a.getSpanId().toByteArray()));
		var attributes = a.getAttributesList();
		assertEquals(List.of("thread.name", "thread.id", "requestId", "exception.type", "exception.message",
				"exception.stacktrace"), attributes.stream().map(KeyValue::getKey).toList());
		assertEquals(42, attributes.get(1).getValue().getIntValue());
		assertEquals("abc-123", attributes.get(2).getValue().getStringValue());
		assertEquals(NoStackException.class.getName(), attributes.get(3).getValue().getStringValue());
		assertEquals("bad", attributes.get(4).getValue().getStringValue());
		assertTrue(attributes.get(5).getValue().getStringValue().startsWith(NoStackException.class.getName()));

		LogRecord b = resourceLogs.getScopeLogs(1).getLogRecords(0);
		assertEquals(SeverityNumber.SEVERITY_NUMBER_WARN, b.getSeverityNumber());
		assertEquals("WARN", b.getSeverityText());
		assertTrue(b.getTraceId().isEmpty());
		assertTrue(b.getSpanId().isEmpty());
		assertEquals(List.of("thread.name", "thread.id"),
				b.getAttributesList().stream().map(KeyValue::getKey).toList());
	}

	@Test
	void invalidTraceIdStaysAnAttribute() throws Exception {
		var kvs = new LinkedHashMap<String, String>();
		kvs.put("traceId", "not-hex");
		kvs.put("spanId", SPAN_ID);
		var record = OtlpRecord.of(event("x", Level.INFO, "m", kvs, null), "traceId", "spanId");
		var request = ExportLogsServiceRequest
			.parseFrom(OtlpProtobuf.encode(OtlpResource.of(null, null, EnvironmentVariables.OFF), List.of(record)));
		var r = request.getResourceLogs(0).getScopeLogs(0).getLogRecords(0);
		assertTrue(r.getTraceId().isEmpty());
		// A span id without a valid trace id is meaningless, so it also stays an
		// attribute.
		assertEquals(Map.of("thread.name", "main", "traceId", "not-hex", "spanId", SPAN_ID),
				strings(r.getAttributesList()));
		assertEquals(Map.of("service.name", "unknown_service:java"),
				strings(request.getResourceLogs(0).getResource().getAttributesList()));
	}

	private static Map<String, String> strings(List<KeyValue> kvs) {
		return kvs.stream()
			.filter(kv -> kv.getValue().getValueCase() == AnyValue.ValueCase.STRING_VALUE)
			.collect(Collectors.toMap(KeyValue::getKey, kv -> kv.getValue().getStringValue()));
	}

}

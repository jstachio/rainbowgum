package io.jstach.rainbowgum.otlp;

import java.util.List;
import java.util.Map;

import org.jspecify.annotations.Nullable;

import io.jstach.rainbowgum.json.JsonBuffer;
import io.jstach.rainbowgum.json.JsonBuffer.JSONToken;

/*
 * Writes an OTLP/JSON LogsData (same shape as ExportLogsServiceRequest): lowerCamelCase
 * field names, 64 bit integers as decimal strings, enums as integers, and trace and span
 * ids as lowercase hex, as the OTLP specification requires.
 */
final class OtlpJson {

	private OtlpJson() {
	}

	static void write(JsonBuffer b, OtlpResource resource, List<OtlpRecord> records) {
		b.write(JSONToken.OBJECT_START);
		int resourceLogs = b.writeArrayStart("resourceLogs", 0, 0);
		b.writeArrayElementObjectStart(resourceLogs);

		int resourceAttributes = b.writeArrayStart("attributes", b.writeObjectStart("resource", 0, 0), 0);
		for (var e : resource.attributes().entrySet()) {
			writeKeyValue(b, resourceAttributes++, e.getKey(), e.getValue(), 0);
		}
		b.writeArrayEnd();
		b.writeObjectEnd();

		int scopeIndex = b.writeArrayStart("scopeLogs", 1, 0);
		for (var scope : OtlpRecord.byScope(records).entrySet()) {
			b.writeArrayElementObjectStart(scopeIndex++);
			b.write("name", scope.getKey(), b.writeObjectStart("scope", 0, 0));
			b.writeObjectEnd();
			int recordIndex = b.writeArrayStart("logRecords", 1, 0);
			for (var r : scope.getValue()) {
				writeRecord(b, recordIndex++, r);
			}
			b.writeArrayEnd();
			b.writeArrayElementObjectEnd();
		}
		b.writeArrayEnd();

		b.writeArrayElementObjectEnd();
		b.writeArrayEnd();
		b.write(JSONToken.OBJECT_END);
	}

	private static void writeRecord(JsonBuffer b, int index, OtlpRecord r) {
		b.writeArrayElementObjectStart(index);
		String time = Long.toString(r.timeUnixNano());
		int i = b.write("timeUnixNano", time, 0);
		i = b.write("observedTimeUnixNano", time, i);
		i = b.writeInt("severityNumber", r.severityNumber(), i, 0);
		i = b.write("severityText", r.severityText(), i);
		b.write("stringValue", r.body(), b.writeObjectStart("body", i++, 0));
		b.writeObjectEnd();
		int attributeIndex = b.writeArrayStart("attributes", i++, 0);
		for (var a : r.attributes()) {
			var any = a.any();
			if (any != null) {
				b.writeArrayElementObjectStart(attributeIndex++);
				b.writeObjectStart("value", b.write("key", a.key(), 0), 0);
				anyValue(b, any.value());
				b.writeObjectEnd();
				b.writeArrayElementObjectEnd();
				continue;
			}
			var s = a.stringValue();
			writeKeyValue(b, attributeIndex++, a.key(), s, a.intValue());
		}
		b.writeArrayEnd();
		i = b.write("traceId", r.traceId(), i);
		i = b.write("spanId", r.spanId(), i);
		b.write("eventName", r.eventName(), i);
		b.writeArrayElementObjectEnd();
	}

	/*
	 * The members of an AnyValue object for a JsonTree value, written inside an object
	 * already started. int64 is a JSON string, as OTLP/JSON requires.
	 */
	private static void anyValue(JsonBuffer b, @Nullable Object v) {
		switch (v) {
			case null -> {
			}
			case String s -> b.write("stringValue", s, 0);
			case Boolean bool -> b.writeRawJson("boolValue", bool.toString(), 0, 0);
			case Long l -> b.write("intValue", Long.toString(l), 0);
			case Double d -> b.writeDouble("doubleValue", d, 0, 0);
			case List<?> list -> {
				int i = b.writeArrayStart("values", b.writeObjectStart("arrayValue", 0, 0), 0);
				for (Object e : list) {
					b.writeArrayElementObjectStart(i++);
					anyValue(b, e);
					b.writeArrayElementObjectEnd();
				}
				b.writeArrayEnd();
				b.writeObjectEnd();
			}
			case Map<?, ?> map -> {
				int i = b.writeArrayStart("values", b.writeObjectStart("kvlistValue", 0, 0), 0);
				for (var e : map.entrySet()) {
					b.writeArrayElementObjectStart(i++);
					b.writeObjectStart("value", b.write("key", String.valueOf(e.getKey()), 0), 0);
					anyValue(b, e.getValue());
					b.writeObjectEnd();
					b.writeArrayElementObjectEnd();
				}
				b.writeArrayEnd();
				b.writeObjectEnd();
			}
			default -> b.write("stringValue", v.toString(), 0);
		}
	}

	private static void writeKeyValue(JsonBuffer b, int index, String key, @Nullable String stringValue,
			long intValue) {
		b.writeArrayElementObjectStart(index);
		int value = b.writeObjectStart("value", b.write("key", key, 0), 0);
		if (stringValue != null) {
			b.write("stringValue", stringValue, value);
		}
		else {
			b.write("intValue", Long.toString(intValue), value);
		}
		b.writeObjectEnd();
		b.writeArrayElementObjectEnd();
	}

}

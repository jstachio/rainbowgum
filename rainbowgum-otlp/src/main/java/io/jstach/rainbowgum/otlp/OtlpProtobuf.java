package io.jstach.rainbowgum.otlp;

import java.util.HexFormat;
import java.util.List;
import java.util.Map;

import org.jspecify.annotations.Nullable;

/*
 * Encodes opentelemetry.proto.collector.logs.v1.ExportLogsServiceRequest (field numbers
 * from opentelemetry-proto 1.11). LogsData has the same layout, so this is also a valid
 * LogsData message.
 */
final class OtlpProtobuf {

	private OtlpProtobuf() {
	}

	static byte[] encode(OtlpResource resource, List<OtlpRecord> records) {
		var w = new ProtoWriter(Math.max(256, records.size() * 256));
		int resourceLogs = w.startMessage(1);
		int res = w.startMessage(1);
		for (var e : resource.attributes().entrySet()) {
			stringKeyValue(w, 1, e.getKey(), e.getValue());
		}
		w.endMessage(res);
		for (Map.Entry<String, List<OtlpRecord>> scope : OtlpRecord.byScope(records).entrySet()) {
			int scopeLogs = w.startMessage(2);
			int scopeMessage = w.startMessage(1);
			w.stringField(1, scope.getKey());
			w.endMessage(scopeMessage);
			for (var r : scope.getValue()) {
				int record = w.startMessage(2);
				logRecord(w, r);
				w.endMessage(record);
			}
			w.endMessage(scopeLogs);
		}
		w.endMessage(resourceLogs);
		return w.toByteArray();
	}

	private static void logRecord(ProtoWriter w, OtlpRecord r) {
		w.fixed64Field(1, r.timeUnixNano());
		w.varintField(2, r.severityNumber());
		w.stringField(3, r.severityText());
		int body = w.startMessage(5);
		w.stringField(1, r.body());
		w.endMessage(body);
		for (var a : r.attributes()) {
			keyValue(w, 6, a);
		}
		var traceId = r.traceId();
		if (traceId != null) {
			w.bytesField(9, HexFormat.of().parseHex(traceId));
		}
		var spanId = r.spanId();
		if (spanId != null) {
			w.bytesField(10, HexFormat.of().parseHex(spanId));
		}
		w.fixed64Field(11, r.timeUnixNano());
		var eventName = r.eventName();
		if (eventName != null) {
			w.stringField(12, eventName);
		}
	}

	private static void keyValue(ProtoWriter w, int field, OtlpRecord.Attribute a) {
		int kv = w.startMessage(field);
		w.stringField(1, a.key());
		int value = w.startMessage(2);
		var any = a.any();
		var s = a.stringValue();
		if (any != null) {
			anyValue(w, any.value());
		}
		else if (s != null) {
			w.stringField(1, s);
		}
		else {
			w.varintField(3, a.intValue());
		}
		w.endMessage(value);
		w.endMessage(kv);
	}

	/*
	 * The content of an AnyValue message for a JsonTree value.
	 */
	private static void anyValue(ProtoWriter w, @Nullable Object v) {
		switch (v) {
			case null -> {
			}
			case String s -> w.stringField(1, s);
			case Boolean b -> w.varintField(2, b ? 1 : 0);
			case Long l -> w.varintField(3, l);
			case Double d -> w.fixed64Field(4, Double.doubleToRawLongBits(d));
			case List<?> list -> {
				int array = w.startMessage(5);
				for (Object e : list) {
					int element = w.startMessage(1);
					anyValue(w, e);
					w.endMessage(element);
				}
				w.endMessage(array);
			}
			case Map<?, ?> map -> {
				int kvlist = w.startMessage(6);
				for (var e : map.entrySet()) {
					int kv = w.startMessage(1);
					w.stringField(1, String.valueOf(e.getKey()));
					int value = w.startMessage(2);
					anyValue(w, e.getValue());
					w.endMessage(value);
					w.endMessage(kv);
				}
				w.endMessage(kvlist);
			}
			default -> w.stringField(1, v.toString());
		}
	}

	private static void stringKeyValue(ProtoWriter w, int field, String key, String value) {
		int kv = w.startMessage(field);
		w.stringField(1, key);
		int v = w.startMessage(2);
		w.stringField(1, value);
		w.endMessage(v);
		w.endMessage(kv);
	}

}

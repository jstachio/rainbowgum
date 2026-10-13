package io.jstach.rainbowgum.otlp;

import java.util.HexFormat;
import java.util.List;
import java.util.Map;

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
		var s = a.stringValue();
		if (s != null) {
			w.stringField(1, s);
		}
		else {
			w.varintField(3, a.intValue());
		}
		w.endMessage(value);
		w.endMessage(kv);
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

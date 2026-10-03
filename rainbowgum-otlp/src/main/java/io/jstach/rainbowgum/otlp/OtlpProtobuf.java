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
		var resourceLogs = new ProtoWriter();
		var res = new ProtoWriter();
		for (var e : resource.attributes().entrySet()) {
			res.messageField(1, stringKeyValue(e.getKey(), e.getValue()));
		}
		resourceLogs.messageField(1, res);
		for (Map.Entry<String, List<OtlpRecord>> scope : OtlpRecord.byScope(records).entrySet()) {
			var scopeLogs = new ProtoWriter();
			scopeLogs.messageField(1, new ProtoWriter().stringField(1, scope.getKey()));
			for (var r : scope.getValue()) {
				scopeLogs.messageField(2, logRecord(r));
			}
			resourceLogs.messageField(2, scopeLogs);
		}
		return new ProtoWriter().messageField(1, resourceLogs).toByteArray();
	}

	private static ProtoWriter logRecord(OtlpRecord r) {
		var w = new ProtoWriter();
		w.fixed64Field(1, r.timeUnixNano());
		w.varintField(2, r.severityNumber());
		w.stringField(3, r.severityText());
		w.messageField(5, new ProtoWriter().stringField(1, r.body()));
		for (var a : r.attributes()) {
			w.messageField(6, keyValue(a));
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
		return w;
	}

	private static ProtoWriter keyValue(OtlpRecord.Attribute a) {
		var value = new ProtoWriter();
		var s = a.stringValue();
		if (s != null) {
			value.stringField(1, s);
		}
		else {
			value.varintField(3, a.intValue());
		}
		return new ProtoWriter().stringField(1, a.key()).messageField(2, value);
	}

	private static ProtoWriter stringKeyValue(String key, String value) {
		return new ProtoWriter().stringField(1, key).messageField(2, new ProtoWriter().stringField(1, value));
	}

}

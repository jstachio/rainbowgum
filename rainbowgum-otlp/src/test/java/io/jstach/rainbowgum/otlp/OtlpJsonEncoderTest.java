package io.jstach.rainbowgum.otlp;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.lang.System.Logger.Level;
import java.util.LinkedHashMap;
import java.util.Map;

import org.junit.jupiter.api.Test;

import io.jstach.rainbowgum.LogEncoder;
import io.jstach.rainbowgum.LogEvent;
import io.jstach.rainbowgum.output.ListLogOutput;

class OtlpJsonEncoderTest {

	static String encode(LogEncoder encoder, LogEvent event) {
		var out = new ListLogOutput();
		out.write(new LogEvent[] { event }, 1, encoder);
		return out.events().get(0).getValue();
	}

	@Test
	void encodesOneLogsDataPerLine() {
		var kvs = new LinkedHashMap<String, String>();
		kvs.put("traceId", OtlpProtobufTest.TRACE_ID);
		kvs.put("spanId", OtlpProtobufTest.SPAN_ID);
		kvs.put("quote\"key", "line\nbreak");
		var event = OtlpProtobufTest.event("com.example.A", Level.ERROR, "boom", kvs,
				new OtlpProtobufTest.NoStackException("bad"));
		var encoder = OtlpJsonEncoder.of("otlp", "orders", Map.of("host.name", "h1"), null, null, null);
		String expected = """
				{"resourceLogs":[{"resource":{"attributes":[\
				{"key":"service.name","value":{"stringValue":"orders"}},\
				{"key":"host.name","value":{"stringValue":"h1"}}]},\
				"scopeLogs":[{"scope":{"name":"com.example.A"},"logRecords":[{\
				"timeUnixNano":"1700000000123456789","observedTimeUnixNano":"1700000000123456789",\
				"severityNumber":17,"severityText":"ERROR","body":{"stringValue":"boom"},\
				"attributes":[{"key":"thread.name","value":{"stringValue":"main"}},\
				{"key":"thread.id","value":{"intValue":"42"}},\
				{"key":"quote\\"key","value":{"stringValue":"line\\nbreak"}},\
				{"key":"exception.type","value":{"stringValue":"io.jstach.rainbowgum.otlp.OtlpProtobufTest$NoStackException"}},\
				{"key":"exception.message","value":{"stringValue":"bad"}},\
				{"key":"exception.stacktrace","value":{"stringValue":"io.jstach.rainbowgum.otlp.OtlpProtobufTest$NoStackException: bad\\n"}}],\
				"traceId":"4bf92f3577b34da6a3ce929d0e0e4736","spanId":"00f067aa0ba902b7"}]}]}]}
				""";
		assertEquals(expected, encode(encoder, event));
	}

}

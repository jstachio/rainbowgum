package io.jstach.rainbowgum.otlp;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.lang.System.Logger.Level;
import java.net.URI;
import java.util.LinkedHashMap;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import io.jstach.rainbowgum.LogConfig;
import io.jstach.rainbowgum.LogEncoder;
import io.jstach.rainbowgum.LogEvent;
import io.jstach.rainbowgum.LogProperties;
import io.jstach.rainbowgum.LogProviderRef;
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
		// Context must not duplicate or override the event's standard attributes.
		kvs.put("thread.name", "wrong-thread");
		kvs.put("thread.id", "wrong-id");
		kvs.put("exception.type", "wrong-type");
		kvs.put("exception.message", "wrong-message");
		kvs.put("exception.stacktrace", "wrong-stack");

		var event = OtlpProtobufTest.event("com.example.A", Level.ERROR, "boom", kvs,
				new OtlpProtobufTest.NoStackException("bad"));
		var encoder = OtlpJsonEncoder.of("otlp", "orders", Map.of("host.name", "h1"), null, null, null, null, null);
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

	/*
	 * The same capabilities OpenTelemetry's Logback and Log4j appenders have: the event
	 * name key, caller (code) attributes, and include/exclude globs, configured only by
	 * properties.
	 */
	@Test
	void eventNameCallerAndKeyValueGlobsFromProperties() {
		var config = LogConfig.builder().properties(LogProperties.builder().fromProperties("""
				logging.appender.console.encoder.otlp.keyValuesIncluded=request*,user,otel.*
				logging.appender.console.encoder.otlp.keyValuesExcluded=requestSecret
				""").build()).configurator(new OtlpConfigurator()).build();
		var encoder = config.encoderRegistry()
			.provide(LogProviderRef.of(URI.create("otlp:///")))
			.provide("console", config);
		var kvs = new LinkedHashMap<String, String>();
		kvs.put("otel.event.name", "order.shipped");
		kvs.put("requestId", "7f3a9c");
		kvs.put("requestSecret", "hunter2");
		kvs.put("user", "ada");
		kvs.put("users", "excluded by not matching");
		kvs.put("otel.other", "kept");
		var caller = ship();
		var event = LogEvent.withCaller(OtlpProtobufTest.event("com.example.A", Level.INFO, "shipped", kvs, null),
				caller);
		String expected = """
				{"resourceLogs":[{"resource":{"attributes":[\
				{"key":"service.name","value":{"stringValue":"unknown_service:java"}}]},\
				"scopeLogs":[{"scope":{"name":"com.example.A"},"logRecords":[{\
				"timeUnixNano":"1700000000123456789","observedTimeUnixNano":"1700000000123456789",\
				"severityNumber":9,"severityText":"INFO","body":{"stringValue":"shipped"},\
				"attributes":[{"key":"thread.name","value":{"stringValue":"main"}},\
				{"key":"thread.id","value":{"intValue":"42"}},\
				{"key":"code.function.name","value":{"stringValue":"io.jstach.rainbowgum.otlp.OtlpJsonEncoderTest.ship"}},\
				{"key":"code.file.path","value":{"stringValue":"OtlpJsonEncoderTest.java"}},\
				{"key":"code.line.number","value":{"intValue":"LINE"}},\
				{"key":"requestId","value":{"stringValue":"7f3a9c"}},\
				{"key":"user","value":{"stringValue":"ada"}},\
				{"key":"otel.other","value":{"stringValue":"kept"}}],\
				"eventName":"order.shipped"}]}]}]}
				""";
		assertEquals(expected.replace("LINE", Integer.toString(caller.lineNumber())), encode(encoder, event.freeze()));
	}

	@ParameterizedTest
	@CsvSource(delimiter = '|', textBlock = """
			*         | anything     | true
			*         | ''           | true
			''        | ''           | true
			''        | a            | false
			user      | user         | true
			user      | users        | false
			user      | User         | false
			user*     | user         | true
			user*     | user.id      | true
			*.id      | user.id      | true
			*.id      | user.idx     | false
			u?er      | user         | true
			u?er      | uer          | false
			a*b*c     | aXXbYYc      | true
			a*b*c     | aXXbYY       | false
			a*b*c     | abcbc        | true
			**        | x            | true
			?         | ''           | false
			""")
	void globs(String glob, String key, boolean expected) {
		assertEquals(expected, OtlpRecord.KeyFilter.matches(glob, key), glob + " ~ " + key);
	}

	@Test
	void excludeWinsOverInclude() {
		var filter = OtlpRecord.KeyFilter.of(java.util.List.of("user*"), java.util.List.of("user.password"));
		assertEquals(true, filter.test("user.id"));
		assertEquals(false, filter.test("user.password"));
		assertEquals(false, filter.test("requestId"));
		var excludeOnly = OtlpRecord.KeyFilter.of(null, java.util.List.of("*"));
		assertEquals(false, excludeOnly.test("anything"));
	}

	// Stands in for the application method that logged.
	private static LogEvent.Caller ship() {
		return LogEvent.Caller.of(StackWalker.getInstance().walk(s -> s.findFirst().orElseThrow()));
	}

}

package io.jstach.rainbowgum.test.otlpcollector;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Stream;

import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;

import io.jstach.rainbowgum.LogConfig;
import io.jstach.rainbowgum.LogProperties;
import io.jstach.rainbowgum.LogPublisher.PublisherFactory;
import io.jstach.rainbowgum.RainbowGum;
import io.jstach.rainbowgum.otlp.OtlpOutput;
import io.jstach.rainbowgum.otlp.OtlpProtocol;
import io.opentelemetry.proto.collector.logs.v1.ExportLogsServiceRequest;
import io.opentelemetry.proto.common.v1.KeyValue;
import io.opentelemetry.proto.logs.v1.LogRecord;
import io.opentelemetry.sdk.OpenTelemetrySdk;
import io.opentelemetry.sdk.trace.SdkTracerProvider;

@Execution(ExecutionMode.SAME_THREAD)
class OtlpCollectorIT {

	static Stream<Arguments> protocols() {
		return Stream.of(OtlpProtocol.values())
			.flatMap(p -> Stream.of(Arguments.of(p, "none"), Arguments.of(p, "gzip")));
	}

	@ParameterizedTest(name = "{0}, compression={1}")
	@MethodSource("protocols")
	void collectorReceivesCorrelatedLogs(OtlpProtocol protocol, String compression) throws Exception {
		Path directory = Path
			.of("target", "collector", protocol.name().toLowerCase(java.util.Locale.ROOT) + "-" + compression,
					UUID.randomUUID().toString())
			.toAbsolutePath();
		String loggerName = "rainbowgum.collector." + protocol.name() + "." + compression;
		String traceId;
		String spanId;
		var failure = new IllegalArgumentException("test failure");
		failure.setStackTrace(new StackTraceElement[] {
				new StackTraceElement("example.Application", "run", "Application.java", 42) });
		var config = LogConfig.builder()
			.serviceLoader()
			.properties(LogProperties.builder().fromProperties("").build())
			.build();
		try (var collector = CollectorProcess.start(directory);
				var sdk = OpenTelemetrySdk.builder().setTracerProvider(SdkTracerProvider.builder().build()).build()) {
			// A large output batch ensures close() must flush, rather than maxBatchSize.
			try (var gum = RainbowGum.builder(config)
				.route(r -> r.publisher(PublisherFactory.ofAsync(16))
					.appender("otlp",
							a -> a.output(OtlpOutput.of(b -> b.endpoint(collector.endpoint())
								.protocol(protocol)
								.compression(compression)
								.maxBatchSize(100)
								.serviceName("rainbowgum-collector-test")
								.resourceAttributes(Map.of("deployment.environment.name", "test"))))))
				.set()) {
				var logger = LoggerFactory.getLogger(loggerName);
				var span = sdk.getTracer("rainbowgum-collector-test").spanBuilder("log-test").startSpan();
				traceId = span.getSpanContext().getTraceId();
				spanId = span.getSpanContext().getSpanId();
				try (var scope = span.makeCurrent()) {
					logger.info("plain {}", "message");
					MDC.put("request.id", "request-42");
					logger.atWarn().addKeyValue("attempt", "2").log("fluent message");
					logger.error("failure", failure);
				}
				finally {
					MDC.clear();
					span.end();
				}
				logger.info("outside span");
			}
			assertEquals(List.of(),
					config.alerts()
						.dump()
						.stream()
						.filter(e -> e.level() == System.Logger.Level.ERROR || e.level() == System.Logger.Level.WARNING)
						.map(e -> e.message())
						.toList());
		}
		assertTrue(Files.size(directory.resolve("logs.json")) > 0, "Collector exported no readable JSON");
		var requests = readRequests(directory.resolve("logs.pb"));
		var records = new ArrayList<LogRecord>();
		for (var request : requests) {
			for (var resource : request.getResourceLogsList()) {
				assertEquals(Map.of("service.name", "rainbowgum-collector-test", "deployment.environment.name", "test"),
						attributes(resource.getResource().getAttributesList()));
				for (var scope : resource.getScopeLogsList()) {
					assertEquals(loggerName, scope.getScope().getName());
					records.addAll(scope.getLogRecordsList());
				}
			}
		}
		assertEquals(List.of("plain message", "fluent message", "failure", "outside span"),
				records.stream().map(r -> r.getBody().getStringValue()).toList());
		assertEquals(List.of(9, 13, 17, 9), records.stream().map(LogRecord::getSeverityNumberValue).toList());
		for (var record : records.subList(0, 3)) {
			assertEquals(traceId, HexFormat.of().formatHex(record.getTraceId().toByteArray()));
			assertEquals(spanId, HexFormat.of().formatHex(record.getSpanId().toByteArray()));
			assertTrue(record.getTimeUnixNano() > 0);
		}
		assertTrue(records.getLast().getTraceId().isEmpty());
		assertTrue(records.getLast().getSpanId().isEmpty());
		var fluent = attributes(records.get(1).getAttributesList());
		assertEquals("request-42", fluent.get("request.id"));
		assertEquals("2", fluent.get("attempt"));
		var error = attributes(records.get(2).getAttributesList());
		assertEquals("java.lang.IllegalArgumentException", error.get("exception.type"));
		assertEquals("test failure", error.get("exception.message"));
		assertEquals("""
				java.lang.IllegalArgumentException: test failure
					at example.Application.run(Application.java:42)
				""", error.get("exception.stacktrace"));
		for (var record : records) {
			var attrs = attributes(record.getAttributesList());
			assertTrue(attrs.containsKey("thread.name"));
			assertTrue(attrs.containsKey("thread.id"));
			assertEquals(false, attrs.containsKey("traceId"));
			assertEquals(false, attrs.containsKey("spanId"));
		}
	}

	private static Map<String, String> attributes(List<KeyValue> values) {
		var result = new LinkedHashMap<String, String>();
		for (var attribute : values) {
			var value = attribute.getValue();
			String rendered = value.hasStringValue() ? value.getStringValue() : Long.toString(value.getIntValue());
			assertEquals(null, result.put(attribute.getKey(), rendered), "Duplicate attribute " + attribute.getKey());
		}
		return result;
	}

	private static List<ExportLogsServiceRequest> readRequests(Path path) throws IOException {
		var buffer = ByteBuffer.wrap(Files.readAllBytes(path));
		var requests = new ArrayList<ExportLogsServiceRequest>();
		// fileexporter frames each protobuf message with a big-endian 32-bit length.
		while (buffer.hasRemaining()) {
			assertTrue(buffer.remaining() >= Integer.BYTES, "Truncated collector file length");
			int length = buffer.getInt();
			assertTrue(length > 0 && length <= buffer.remaining(), "Truncated collector protobuf payload");
			var message = new byte[length];
			buffer.get(message);
			requests.add(ExportLogsServiceRequest.parseFrom(message));
		}
		assertTrue(!requests.isEmpty(), "Collector exported no log requests");
		return requests;
	}

}

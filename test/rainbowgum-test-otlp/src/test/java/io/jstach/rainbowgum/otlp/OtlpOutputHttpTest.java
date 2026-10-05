package io.jstach.rainbowgum.otlp;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.lang.System.Logger.Level;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.zip.GZIPInputStream;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Isolated;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import com.sun.net.httpserver.HttpServer;

import io.jstach.rainbowgum.KeyValues;
import io.jstach.rainbowgum.LogConfig;
import io.jstach.rainbowgum.LogEvent;
import io.jstach.rainbowgum.LogMetrics;
import io.jstach.rainbowgum.LogPublisher.PublisherFactory;
import io.jstach.rainbowgum.RainbowGum;
import io.opentelemetry.proto.collector.logs.v1.ExportLogsPartialSuccess;
import io.opentelemetry.proto.collector.logs.v1.ExportLogsServiceRequest;
import io.opentelemetry.proto.collector.logs.v1.ExportLogsServiceResponse;

/*
 * OtlpOutput.retryBaseMillis is static, so these tests run isolated.
 */
@Isolated
class OtlpOutputHttpTest {

	record Received(String path, Map<String, List<String>> headers, byte[] body) {

		String header(String name) {
			var v = headers.get(name);
			return v == null ? "" : v.get(0);
		}

	}

	record Reply(int status, byte[] body, Map<String, String> headers) {

		static Reply of(int status) {
			return new Reply(status, new byte[0], Map.of());
		}

	}

	HttpServer server;

	final List<Received> received = new CopyOnWriteArrayList<>();

	final Deque<Reply> replies = new ArrayDeque<>();

	URI endpoint;

	@BeforeEach
	void start() throws IOException {
		OtlpOutput.retryBaseMillis = 1;
		server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
		server.createContext("/", exchange -> {
			received.add(new Received(exchange.getRequestURI().getPath(), Map.copyOf(exchange.getRequestHeaders()),
					exchange.getRequestBody().readAllBytes()));
			Reply reply;
			synchronized (replies) {
				reply = replies.isEmpty() ? Reply.of(200) : replies.poll();
			}
			reply.headers().forEach((k, v) -> exchange.getResponseHeaders().add(k, v));
			exchange.sendResponseHeaders(reply.status(), reply.body().length == 0 ? -1 : reply.body().length);
			if (reply.body().length > 0) {
				exchange.getResponseBody().write(reply.body());
			}
			exchange.close();
		});
		server.start();
		endpoint = URI.create("http://127.0.0.1:" + server.getAddress().getPort() + "/v1/logs");
	}

	@AfterEach
	void stop() {
		server.stop(0);
		OtlpOutput.retryBaseMillis = 1000;
	}

	void reply(Reply... r) {
		synchronized (replies) {
			replies.addAll(List.of(r));
		}
	}

	static LogEvent event(String message) {
		return LogEvent.of(Instant.ofEpochSecond(1_700_000_000L), "main", 1, Level.INFO, "test", message,
				KeyValues.of(), null);
	}

	OtlpOutput output(LogConfig config, java.util.function.Consumer<OtlpOutputBuilder> consumer) {
		return OtlpOutput.of(b -> {
			b.endpoint(endpoint);
			consumer.accept(b);
		}).provide("otlp", config);
	}

	static long metric(LogConfig config, String name) {
		return config.metrics()
			.snapshot()
			.stream()
			.filter(c -> c.name().equals(name))
			.mapToLong(LogMetrics.Metric::value)
			.sum();
	}

	@Test
	void asyncPublisherBatchesProtobufRequests() throws Exception {
		var config = LogConfig.builder().build();
		var gum = RainbowGum.builder(config).route(r -> {
			r.appender("otlp", a -> a.output(OtlpOutput
				.of(b -> b.endpoint(endpoint).serviceName("orders").headers(Map.of("Authorization", "Bearer token")))));
			r.publisher(PublisherFactory.ofAsync(100));
		}).build();
		int count = 50;
		try (var g = gum.start()) {
			for (int i = 0; i < count; i++) {
				g.log(event("m" + i));
			}
		}
		int records = 0;
		for (var r : received) {
			assertEquals("/v1/logs", r.path());
			assertEquals("application/x-protobuf", r.header("Content-type"));
			assertEquals("Bearer token", r.header("Authorization"));
			var request = ExportLogsServiceRequest.parseFrom(r.body());
			assertEquals("orders",
					request.getResourceLogs(0).getResource().getAttributes(0).getValue().getStringValue());
			records += request.getResourceLogs(0).getScopeLogs(0).getLogRecordsCount();
		}
		assertEquals(count, records);
		assertTrue(received.size() < count, () -> "expected batching, got " + received.size() + " requests");
	}

	@Test
	void jsonWithGzip() throws Exception {
		var config = LogConfig.builder().build();
		var output = output(config, b -> b.protocol(OtlpProtocol.HTTP_JSON).compression("gzip"));
		output.write(event("hello"), new byte[0], 0, 0, null);
		output.close();
		assertEquals(1, received.size());
		var r = received.get(0);
		assertEquals("application/json", r.header("Content-type"));
		assertEquals("gzip", r.header("Content-encoding"));
		String json;
		try (var in = new GZIPInputStream(new ByteArrayInputStream(r.body()))) {
			json = new String(in.readAllBytes(), StandardCharsets.UTF_8);
		}
		assertTrue(json.startsWith("{\"resourceLogs\":[{\"resource\":{\"attributes\":[{\"key\":\"service.name\""),
				json);
		assertTrue(json.contains("\"body\":{\"stringValue\":\"hello\"}"), json);
	}

	@Test
	void retriesThenSucceeds() {
		reply(Reply.of(503), new Reply(429, new byte[0], Map.of("Retry-After", "0")), Reply.of(200));
		var config = LogConfig.builder().build();
		var output = output(config, b -> {
		});
		output.write(event("x"), new byte[0], 0, 0, null);
		output.close();
		assertEquals(3, received.size());
		assertEquals(0, metric(config, OtlpOutput.FAILED_METRIC));
	}

	@Test
	void nonRetryableStatusDropsBatchWithAlert() {
		reply(new Reply(400, "bad request".getBytes(StandardCharsets.UTF_8), Map.of()));
		var config = LogConfig.builder().build();
		var output = output(config, b -> {
		});
		output.write(event("a"), new byte[0], 0, 0, null);
		output.write(event("b"), new byte[0], 0, 0, null);
		output.close();
		assertEquals(1, received.size());
		assertEquals(2, metric(config, OtlpOutput.FAILED_METRIC));
		var alert = config.alerts().dump().get(config.alerts().dump().size() - 1);
		assertEquals("OTLP export to " + endpoint + " failed; dropped 2 log records", alert.message());
		assertEquals("java.io.IOException: HTTP 400 from " + endpoint + ": bad request",
				String.valueOf(alert.throwableOrNull()));
	}

	@Test
	void retryableStatusGivesUpAfterMaxRetries() {
		reply(Reply.of(503), Reply.of(503), Reply.of(503), Reply.of(503), Reply.of(503));
		var config = LogConfig.builder().build();
		var output = output(config, b -> {
		});
		output.write(event("x"), new byte[0], 0, 0, null);
		output.close();
		assertEquals(OtlpOutput.MAX_RETRIES + 1, received.size());
		assertEquals(1, metric(config, OtlpOutput.FAILED_METRIC));
	}

	@Test
	void connectionRefusedIsRetriedThenDropped() {
		server.stop(0);
		var config = LogConfig.builder().build();
		var output = output(config, b -> {
		});
		output.write(event("x"), new byte[0], 0, 0, null);
		output.close();
		assertEquals(1, metric(config, OtlpOutput.FAILED_METRIC));
		var alert = config.alerts().dump().get(config.alerts().dump().size() - 1);
		assertEquals("OTLP export to " + endpoint + " failed; dropped 1 log records", alert.message());
	}

	@Test
	void protobufPartialSuccessIsAWarning() {
		var response = ExportLogsServiceResponse.newBuilder()
			.setPartialSuccess(
					ExportLogsPartialSuccess.newBuilder().setRejectedLogRecords(2).setErrorMessage("too big").build())
			.build()
			.toByteArray();
		reply(new Reply(200, response, Map.of("Content-Type", "application/x-protobuf")));
		var config = LogConfig.builder().build();
		var output = output(config, b -> {
		});
		for (var m : List.of("a", "b", "c")) {
			output.write(event(m), new byte[0], 0, 0, null);
		}
		output.close();
		assertEquals(2, metric(config, OtlpOutput.REJECTED_METRIC));
		var alert = config.alerts().dump().get(config.alerts().dump().size() - 1);
		assertEquals(Level.WARNING, alert.level());
		assertEquals("OTLP endpoint " + endpoint + " rejected 2 of 3 log records: too big", alert.message());
	}

	@Test
	void jsonPartialSuccessIsAWarning() {
		reply(new Reply(200, "{\"partialSuccess\":{\"rejectedLogRecords\":\"1\",\"errorMessage\":\"dup\"}}"
			.getBytes(StandardCharsets.UTF_8), Map.of("Content-Type", "application/json")));
		var config = LogConfig.builder().build();
		var output = output(config, b -> b.protocol(OtlpProtocol.HTTP_JSON));
		output.write(event("a"), new byte[0], 0, 0, null);
		output.close();
		assertEquals(1, metric(config, OtlpOutput.REJECTED_METRIC));
		var alert = config.alerts().dump().get(config.alerts().dump().size() - 1);
		assertEquals("OTLP endpoint " + endpoint + " rejected 1 of 1 log records: dup", alert.message());
	}

	@Test
	void endpointCredentialsAreRedactedInFailureDiagnostics() {
		endpoint = URI
			.create(endpoint.toString().replace("http://", "http://user:kenny@") + "?password=kenny&tenant=test");
		reply(new Reply(400, "bad request".getBytes(StandardCharsets.UTF_8), Map.of()));
		var config = LogConfig.builder().build();
		var output = output(config, b -> {
		});
		output.write(event("a"), new byte[0], 0, 0, null);
		output.close();
		var alert = config.alerts().dump().getLast();
		assertEquals(
				"""
						OTLP export to http://<REDACTED>@localhost/v1/logs?password=<REDACTED>&tenant=test failed; dropped 1 log records""",
				normalizeEndpoint(alert.message()));
		assertEquals(
				"""
						java.io.IOException: HTTP 400 from http://<REDACTED>@localhost/v1/logs?password=<REDACTED>&tenant=test: bad request""",
				normalizeEndpoint(String.valueOf(alert.throwableOrNull())));
	}

	@Test
	void invalidCredentialEndpointDoesNotLeakThroughExceptionCause() {
		endpoint = URI.create("ftp://user:kenny@localhost/v1/logs?token=secret");
		var config = LogConfig.builder().build();
		var output = output(config, b -> {
		});
		output.write(event("a"), new byte[0], 0, 0, null);
		output.close();
		var alert = config.alerts().dump().getLast();
		assertEquals("""
				OTLP export to ftp://<REDACTED>@localhost/v1/logs?token=<REDACTED> failed; dropped 1 log records""",
				alert.message());
		var cause = java.util.Objects.requireNonNull(alert.throwableOrNull());
		assertEquals(
				"""
						java.io.IOException: OTLP request failed (java.lang.IllegalArgumentException); endpoint credentials omitted""",
				cause.toString());
		assertNull(cause.getCause());
	}

	private String normalizeEndpoint(String message) {
		return message.replace("127.0.0.1:" + server.getAddress().getPort(), "localhost");
	}

	@ParameterizedTest
	@EnumSource(OtlpProtocol.class)
	void warningsWithoutRejectionsAreReportedAndRedacted(OtlpProtocol protocol) {
		endpoint = URI.create(endpoint + "?token=secret");
		byte[] response = switch (protocol) {
			case HTTP_JSON -> """
					{"partialSuccess":{"rejectedLogRecords":"0","errorMessage":"deprecated"}}"""
				.getBytes(StandardCharsets.UTF_8);
			case HTTP_PROTOBUF -> ExportLogsServiceResponse.newBuilder()
				.setPartialSuccess(ExportLogsPartialSuccess.newBuilder().setErrorMessage("deprecated"))
				.build()
				.toByteArray();
		};
		reply(new Reply(200, response, Map.of("Content-Type", protocol.contentType())));
		var config = LogConfig.builder().build();
		var output = output(config, b -> b.protocol(protocol));
		output.write(event("a"), new byte[0], 0, 0, null);
		output.close();
		assertEquals(1, received.size());
		assertEquals(0, metric(config, OtlpOutput.REJECTED_METRIC));
		assertEquals(0, metric(config, OtlpOutput.FAILED_METRIC));
		var alert = config.alerts().dump().getLast();
		assertEquals(Level.WARNING, alert.level());
		assertEquals(
				"""
						OTLP endpoint http://localhost/v1/logs?token=<REDACTED> returned a warning for 1 log records: deprecated""",
				normalizeEndpoint(alert.message()));
	}

	@Test
	void malformedResponseIsReportedWithoutRetryingAcceptedRecords() {
		reply(new Reply(200, new byte[] { 10, 5, 8 }, Map.of("Content-Type", "application/x-protobuf")));
		var config = LogConfig.builder().build();
		var output = output(config, b -> {
		});
		output.write(event("a"), new byte[0], 0, 0, null);
		output.flush();
		var alert = config.alerts().dump().getLast();
		assertEquals("""
				Invalid OTLP response from http://localhost/v1/logs""", normalizeEndpoint(alert.message()));
		assertEquals("""
				java.lang.IllegalArgumentException: Invalid protobuf field length""",
				String.valueOf(alert.throwableOrNull()));
		output.write(event("b"), new byte[0], 0, 0, null);
		output.close();
		assertEquals(2, received.size());
	}

	@Test
	void timeoutBoundsResponseBodyWaitAndClose() throws Exception {
		var headersSent = new CountDownLatch(1);
		var releaseBody = new CountDownLatch(1);
		server.removeContext("/");
		server.createContext("/", exchange -> {
			exchange.getRequestBody().readAllBytes();
			exchange.sendResponseHeaders(200, 2);
			exchange.getResponseBody().flush();
			headersSent.countDown();
			try {
				releaseBody.await(5, TimeUnit.SECONDS);
			}
			catch (InterruptedException e) {
				Thread.currentThread().interrupt();
			}
			finally {
				exchange.close();
			}
		});
		var config = LogConfig.builder().build();
		var output = output(config, b -> b.timeout(250));
		output.write(event("a"), new byte[0], 0, 0, null);
		var future = CompletableFuture.runAsync(output::close);
		try {
			assertTrue(headersSent.await(2, TimeUnit.SECONDS));
			// Completion must not depend on the server releasing its response body.
			future.get(2, TimeUnit.SECONDS);
			assertEquals(1, metric(config, OtlpOutput.FAILED_METRIC));
			assertEquals("""
					java.net.http.HttpTimeoutException: OTLP export timed out after 250 ms""",
					String.valueOf(config.alerts().dump().getLast().throwableOrNull()));
		}
		finally {
			releaseBody.countDown();
			future.get(5, TimeUnit.SECONDS);
		}
	}

	@Test
	void retryAfterCannotExtendExportDeadline() throws Exception {
		reply(new Reply(503, new byte[0], Map.of("Retry-After", "30")));
		var config = LogConfig.builder().build();
		var output = output(config, b -> b.timeout(250));
		output.write(event("a"), new byte[0], 0, 0, null);
		CompletableFuture.runAsync(output::close).get(2, TimeUnit.SECONDS);
		assertEquals(1, received.size());
		assertEquals(1, metric(config, OtlpOutput.FAILED_METRIC));
	}

	@Test
	void interruptedWorkerStillFinishesItsCurrentRequest() throws Exception {
		var requestReceived = new CountDownLatch(1);
		var releaseResponse = new CountDownLatch(1);
		server.removeContext("/");
		server.createContext("/", exchange -> {
			exchange.getRequestBody().readAllBytes();
			requestReceived.countDown();
			try {
				releaseResponse.await(5, TimeUnit.SECONDS);
				exchange.sendResponseHeaders(200, -1);
			}
			catch (InterruptedException ex) {
				Thread.currentThread().interrupt();
			}
			finally {
				exchange.close();
			}
		});
		var config = LogConfig.builder().build();
		var output = output(config, b -> b.timeout(2000));
		output.write(event("a"), new byte[0], 0, 0, null);
		var result = new CompletableFuture<Boolean>();
		var worker = Thread.ofPlatform().start(() -> {
			try {
				output.flush();
				result.complete(Thread.currentThread().isInterrupted());
			}
			catch (Throwable e) {
				result.completeExceptionally(e);
			}
		});
		try {
			assertTrue(requestReceived.await(2, TimeUnit.SECONDS));
			worker.interrupt();
			releaseResponse.countDown();
			assertEquals(true, result.get(3, TimeUnit.SECONDS));
			assertEquals(0, metric(config, OtlpOutput.FAILED_METRIC));
		}
		finally {
			releaseResponse.countDown();
			worker.join(5000);
			output.close();
		}
	}

}

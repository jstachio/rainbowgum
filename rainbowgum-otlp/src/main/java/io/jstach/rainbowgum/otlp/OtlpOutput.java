package io.jstach.rainbowgum.otlp;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.lang.System.Logger.Level;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Consumer;
import java.util.regex.Pattern;
import java.util.zip.GZIPOutputStream;

import org.jspecify.annotations.Nullable;

import io.jstach.rainbowgum.KeyValues;
import io.jstach.rainbowgum.LogAlerts;
import io.jstach.rainbowgum.LogConfig;
import io.jstach.rainbowgum.LogEncoder;
import io.jstach.rainbowgum.LogEvent;
import io.jstach.rainbowgum.LogMetrics;
import io.jstach.rainbowgum.LogOutput;
import io.jstach.rainbowgum.LogProperties;
import io.jstach.rainbowgum.LogProperty;
import io.jstach.rainbowgum.LogProvider;
import io.jstach.rainbowgum.annotation.LogConfigurable;
import io.jstach.rainbowgum.annotation.LogConfigurable.ConvertParameter;
import io.jstach.rainbowgum.json.JsonBuffer;
import io.jstach.rainbowgum.json.JsonBuffer.ExtendedFieldPrefix;

/**
 * Sends log events to an OTLP/HTTP endpoint ({@code /v1/logs}) as protobuf (the default)
 * or JSON, without the OpenTelemetry SDK or a protobuf library.
 * <p>
 * Events are buffered and sent as one request per {@link #flush()}, and as soon as
 * {@code maxBatchSize} events are pending. Use an asynchronous publisher: with the
 * default synchronous one every log call waits for its own HTTP request. HTTP 429, 502,
 * 503, and 504 responses and network errors are retried up to three times with
 * exponential backoff with jitter, honoring {@code Retry-After}. The timeout bounds the
 * whole export, including retries and response-body reads. A batch that still fails is
 * dropped, recorded as an error alert, and counted in {@value #FAILED_METRIC}. Records
 * the endpoint reports as rejected (partial success) are a warning alert and counted in
 * {@value #REJECTED_METRIC}.
 * <p>
 * This output encodes events itself, so it does not use an appender encoder. Records have
 * the same content as {@link OtlpJsonEncoder} produces.
 */
public final class OtlpOutput implements LogOutput, LogOutput.ProvidesEncoder {

	/**
	 * Output URI scheme.
	 */
	public static final String OTLP_SCHEME = "otlp";

	/**
	 * Metric counting log records dropped because a request failed.
	 */
	public static final String FAILED_METRIC = "otlp.records.failed";

	/**
	 * Metric counting log records the endpoint rejected in a partial success response.
	 */
	public static final String REJECTED_METRIC = "otlp.records.rejected";

	/**
	 * Default maximum number of events per request.
	 */
	public static final int DEFAULT_MAX_BATCH_SIZE = 512;

	/**
	 * Default export timeout in milliseconds.
	 */
	public static final int DEFAULT_TIMEOUT = 10_000;

	static final int MAX_RETRIES = 3;

	// Tests shorten this; production backoff is 1s, 2s, 4s.
	static volatile long retryBaseMillis = 1000;

	private static final Set<Integer> RETRYABLE = Set.of(429, 502, 503, 504);

	private static final Pattern REJECTED_JSON = Pattern.compile("\"rejectedLogRecords\"\\s*:\\s*\"?(\\d+)");

	private static final Pattern ERROR_JSON = Pattern.compile("\"errorMessage\"\\s*:\\s*\"((?:[^\"\\\\]|\\\\.)*)\"");

	private static final LogEvent PLACEHOLDER = LogEvent.of(Instant.EPOCH, "", 0, Level.INFO, "", "", KeyValues.of(),
			null);

	private final URI endpoint;

	private final String displayEndpoint;

	private final OtlpProtocol protocol;

	private final Map<String, String> headers;

	private final boolean gzip;

	private final Duration timeout;

	private final int maxBatchSize;

	private final OtlpResource resource;

	private final String traceIdKey;

	private final String spanIdKey;

	private final LogAlerts alerts;

	private final LogMetrics metrics;

	private final HttpClient client;

	private final List<OtlpRecord> pending = new ArrayList<>();

	OtlpOutput(URI endpoint, OtlpProtocol protocol, Map<String, String> headers, boolean gzip, Duration timeout,
			int maxBatchSize, OtlpResource resource, String traceIdKey, String spanIdKey, LogConfig config) {
		this.endpoint = endpoint;
		this.displayEndpoint = LogProperty.redactUri(endpoint);
		this.protocol = protocol;
		this.headers = Map.copyOf(headers);
		this.gzip = gzip;
		this.timeout = timeout;
		this.maxBatchSize = maxBatchSize;
		this.resource = resource;
		this.traceIdKey = traceIdKey;
		this.spanIdKey = spanIdKey;
		this.alerts = config.alerts();
		this.metrics = config.metrics();
		this.client = HttpClient.newBuilder().connectTimeout(timeout).build();
	}

	/**
	 * Creates the output with a builder lambda; properties are applied after the lambda.
	 * @param consumer configures the builder.
	 * @return output provider.
	 */
	public static LogProvider<OtlpOutput> of(Consumer<OtlpOutputBuilder> consumer) {
		return (name, config) -> {
			var b = new OtlpOutputBuilder(name);
			consumer.accept(b);
			return b.fromProperties(config.properties()).build().provide(name, config);
		};
	}

	/**
	 * Creates the output. Any value not set falls back to the matching
	 * {@code OTEL_EXPORTER_OTLP_*} environment variable when environment variables are
	 * enabled, then to the OTLP default.
	 * @param name output name.
	 * @param endpoint full URL to post to, default {@code http://localhost:4318/v1/logs}.
	 * @param protocol {@code http/protobuf} (default) or {@code http/json}.
	 * @param headers extra request headers, for example authentication.
	 * @param compression {@code gzip} or {@code none} (default).
	 * @param timeout export timeout in milliseconds, including retries, default
	 * {@value OtlpOutput#DEFAULT_TIMEOUT}.
	 * @param maxBatchSize maximum events per request, default
	 * {@value OtlpOutput#DEFAULT_MAX_BATCH_SIZE}.
	 * @param serviceName {@code service.name} resource attribute, default
	 * {@code unknown_service:java}.
	 * @param resourceAttributes additional resource attributes.
	 * @param traceIdKey key value holding the trace id, default {@code traceId}.
	 * @param spanIdKey key value holding the span id, default {@code spanId}.
	 * @param environmentVariables whether the standard {@code OTEL_*} environment
	 * variables are read, default off.
	 * @return output provider.
	 */
	@LogConfigurable(name = "OtlpOutputBuilder", prefix = LogProperties.OUTPUT_PREFIX)
	static LogProvider<OtlpOutput> of(@LogConfigurable.KeyParameter String name, @Nullable URI endpoint,
			@ConvertParameter("parseProtocol") @Nullable OtlpProtocol protocol, @Nullable Map<String, String> headers,
			@Nullable String compression, @Nullable Integer timeout, @Nullable Integer maxBatchSize,
			@Nullable String serviceName, @Nullable Map<String, String> resourceAttributes, @Nullable String traceIdKey,
			@Nullable String spanIdKey,
			@ConvertParameter("parseEnvironmentVariables") @Nullable EnvironmentVariables environmentVariables) {
		boolean env = environmentVariables == EnvironmentVariables.ON;
		URI endpoint_ = endpoint;
		if (endpoint_ == null) {
			var fromEnv = env ? OtlpEnvironment.logsEndpoint() : null;
			endpoint_ = URI.create(fromEnv != null ? fromEnv : OtlpEnvironment.DEFAULT_ENDPOINT);
		}
		OtlpProtocol protocol_ = protocol;
		if (protocol_ == null) {
			var fromEnv = env ? OtlpEnvironment.logsValue("PROTOCOL") : null;
			protocol_ = fromEnv != null ? OtlpProtocol.parse(fromEnv) : OtlpProtocol.HTTP_PROTOBUF;
		}
		var headers_ = new LinkedHashMap<String, String>();
		if (env) {
			headers_.putAll(OtlpEnvironment.headers());
		}
		if (headers != null) {
			headers_.putAll(headers);
		}
		String compression_ = compression != null ? compression : env ? OtlpEnvironment.logsValue("COMPRESSION") : null;
		boolean gzip = parseCompression(compression_);
		Integer timeout_ = timeout;
		if (timeout_ == null) {
			var fromEnv = env ? OtlpEnvironment.logsValue("TIMEOUT") : null;
			timeout_ = fromEnv != null ? parsePositive("OTEL_EXPORTER_OTLP_TIMEOUT", fromEnv) : DEFAULT_TIMEOUT;
		}
		int maxBatchSize_ = maxBatchSize == null ? DEFAULT_MAX_BATCH_SIZE : maxBatchSize;
		if (maxBatchSize_ <= 0) {
			throw new IllegalArgumentException("maxBatchSize must be positive: " + maxBatchSize_);
		}
		if (timeout_ <= 0) {
			throw new IllegalArgumentException("timeout must be positive: " + timeout_);
		}
		var resource = OtlpResource.of(serviceName, resourceAttributes,
				env ? EnvironmentVariables.ON : EnvironmentVariables.OFF);
		URI e = endpoint_;
		OtlpProtocol p = protocol_;
		Duration t = Duration.ofMillis(timeout_);
		String traceIdKey_ = traceIdKey == null ? OtlpJsonEncoder.DEFAULT_TRACE_ID_KEY : traceIdKey;
		String spanIdKey_ = spanIdKey == null ? OtlpJsonEncoder.DEFAULT_SPAN_ID_KEY : spanIdKey;
		return (n, config) -> new OtlpOutput(e, p, headers_, gzip, t, maxBatchSize_, resource, traceIdKey_, spanIdKey_,
				config);
	}

	static OtlpProtocol parseProtocol(String value) {
		return OtlpProtocol.parse(value);
	}

	static EnvironmentVariables parseEnvironmentVariables(String value) {
		return EnvironmentVariables.parse(value);
	}

	private static boolean parseCompression(@Nullable String value) {
		if (value == null) {
			return false;
		}
		return switch (value.strip().toLowerCase(Locale.ROOT)) {
			case "gzip" -> true;
			case "none" -> false;
			default -> throw new IllegalArgumentException(
					"Invalid OTLP compression: '" + value + "'. Expected one of: gzip, none.");
		};
	}

	private static int parsePositive(String name, String value) {
		try {
			return Integer.parseInt(value.strip());
		}
		catch (NumberFormatException ex) {
			throw new IllegalArgumentException("Invalid " + name + ": '" + value + "'. Expected milliseconds.", ex);
		}
	}

	/**
	 * The endpoint requests are sent to.
	 * @return endpoint URL.
	 */
	public URI endpoint() {
		return endpoint;
	}

	/**
	 * The payload encoding.
	 * @return protocol.
	 */
	public OtlpProtocol protocol() {
		return protocol;
	}

	@Override
	public URI uri() {
		return endpoint;
	}

	@Override
	public OutputType type() {
		return OutputType.NETWORK;
	}

	@Override
	public LogEncoder encoder(String name, LogConfig config) {
		return PassThroughEncoder.INSTANCE;
	}

	@Override
	public Policy policy() {
		return Policy.MANDATORY;
	}

	@Override
	public void write(LogEvent event, byte[] bytes, int off, int len, ContentType contentType) {
		add(event);
	}

	@Override
	public void write(LogEvent[] events, int count, LogEncoder encoder, LogEncoder.Buffer buffer) {
		for (int i = 0; i < count; i++) {
			add(events[i]);
		}
	}

	private void add(LogEvent event) {
		pending.add(OtlpRecord.of(event, traceIdKey, spanIdKey));
		if (pending.size() >= maxBatchSize) {
			send();
		}
	}

	@Override
	public void flush() {
		send();
	}

	@Override
	public void close() {
		try {
			send();
		}
		finally {
			client.close();
		}
	}

	private void send() {
		if (pending.isEmpty()) {
			return;
		}
		/*
		 * The async publisher interrupts its thread on shutdown and then drains the
		 * remaining events on it. Clear the flag so those final batches are still sent
		 * (as FileChannelOutput does, see LOGBACK-875), but skip retries so a down
		 * endpoint cannot stall shutdown. The flag is restored afterwards.
		 */
		boolean interrupted = Thread.interrupted();
		try {
			sendPending(interrupted ? 0 : MAX_RETRIES);
		}
		finally {
			if (interrupted) {
				Thread.currentThread().interrupt();
			}
		}
	}

	private void sendPending(int maxRetries) {
		var records = List.copyOf(pending);
		pending.clear();
		int count = records.size();
		long deadline = System.nanoTime() + timeout.toNanos();
		HttpRequest request;
		try {
			request = request(encode(records));
		}
		catch (RuntimeException ex) {
			requestFailed(count, ex);
			return;
		}
		for (int attempt = 0;; attempt++) {
			HttpResponse<byte[]> response;
			try {
				response = sendRequest(request, deadline);
			}
			catch (IOException | RuntimeException ex) {
				if (attempt < maxRetries && sleep(backoff(attempt), deadline)) {
					continue;
				}
				requestFailed(count, ex);
				return;
			}
			int status = response.statusCode();
			if (status >= 200 && status < 300) {
				try {
					partialSuccess(response.body(), count);
				}
				catch (IllegalArgumentException ex) {
					// A successful HTTP response must not be retried after a decoding
					// failure: the collector may already have accepted these records.
					alerts.error(OtlpOutput.class, "Invalid OTLP response from " + displayEndpoint, ex);
				}
				return;
			}
			if (RETRYABLE.contains(status) && attempt < maxRetries && sleep(retryDelay(attempt, response), deadline)) {
				continue;
			}
			failed(count,
					new IOException("HTTP " + status + " from " + displayEndpoint + ": " + preview(response.body())));
			return;
		}
	}

	private HttpResponse<byte[]> sendRequest(HttpRequest request, long deadline) throws IOException {
		long remaining = deadline - System.nanoTime();
		if (remaining <= 0) {
			throw exportTimeout();
		}
		var future = client.sendAsync(request, HttpResponse.BodyHandlers.ofByteArray());
		boolean interrupted = false;
		try {
			while (true) {
				try {
					return future.get(Math.max(0, deadline - System.nanoTime()), TimeUnit.NANOSECONDS);
				}
				catch (InterruptedException ex) {
					// Preserve shutdown draining even if a publisher interrupts its
					// worker.
					// The deadline still bounds this wait; the flag prevents later
					// retries.
					interrupted = true;
				}
			}
		}
		catch (TimeoutException ex) {
			throw exportTimeout();
		}
		catch (ExecutionException ex) {
			var cause = ex.getCause();
			if (cause instanceof HttpTimeoutException) {
				throw exportTimeout();
			}
			if (cause instanceof IOException io) {
				throw io;
			}
			throw new IOException("OTLP request failed", cause);
		}
		finally {
			// HttpRequest.timeout does not cover the response body on Java 21.
			// Cancel the actual HTTP operation so close() cannot wait for it forever.
			if (!future.isDone()) {
				future.cancel(true);
			}
			if (interrupted) {
				Thread.currentThread().interrupt();
			}
		}
	}

	private HttpTimeoutException exportTimeout() {
		return new HttpTimeoutException("OTLP export timed out after " + timeout.toMillis() + " ms");
	}

	private void requestFailed(int count, Exception cause) {
		if (!displayEndpoint.equals(endpoint.toString())) {
			// HTTP client exception messages and their causes may include credentials.
			// Keep the failure type and stack without retaining unsafe diagnostic text.
			var safe = new IOException(
					"OTLP request failed (" + cause.getClass().getName() + "); endpoint credentials omitted");
			safe.setStackTrace(cause.getStackTrace());
			failed(count, safe);
		}
		else {
			failed(count, cause);
		}
	}

	private byte[] encode(List<OtlpRecord> records) {
		if (protocol == OtlpProtocol.HTTP_PROTOBUF) {
			return OtlpProtobuf.encode(resource, records);
		}
		var json = new JsonBuffer(false, ExtendedFieldPrefix.AT, -1);
		OtlpJson.write(json, resource, records);
		var capture = new ByteCapture();
		json.drain(capture, PLACEHOLDER);
		return capture.bytes.toByteArray();
	}

	private HttpRequest request(byte[] body) {
		var builder = HttpRequest.newBuilder(endpoint).timeout(timeout).header("Content-Type", protocol.contentType());
		headers.forEach(builder::header);
		if (gzip) {
			body = gzip(body);
			builder.header("Content-Encoding", "gzip");
		}
		return builder.POST(HttpRequest.BodyPublishers.ofByteArray(body)).build();
	}

	private static byte[] gzip(byte[] body) {
		var out = new ByteArrayOutputStream(body.length / 2 + 16);
		try (var gz = new GZIPOutputStream(out)) {
			gz.write(body);
		}
		catch (IOException ex) {
			throw new UncheckedIOException(ex);
		}
		return out.toByteArray();
	}

	private static long backoff(int attempt) {
		return backoff(attempt, ThreadLocalRandom.current().nextDouble());
	}

	static long backoff(int attempt, double random) {
		long cap = retryBaseMillis << attempt;
		// Equal jitter keeps a minimum delay while spreading clients across the
		// upper half of the exponentially increasing interval.
		return cap / 2 + (long) ((cap - cap / 2) * random);
	}

	private static long retryDelay(int attempt, HttpResponse<?> response) {
		var retryAfter = response.headers().firstValue("Retry-After");
		if (retryAfter.isPresent()) {
			try {
				return Math.min(Math.max(0, Long.parseLong(retryAfter.get().strip())), 30) * 1000;
			}
			catch (NumberFormatException ex) {
				// HTTP date form is not supported; fall back to exponential backoff.
			}
		}
		return backoff(attempt);
	}

	private static boolean sleep(long millis, long deadline) {
		if (TimeUnit.MILLISECONDS.toNanos(millis) >= deadline - System.nanoTime()) {
			return false;
		}
		try {
			Thread.sleep(millis);
			return true;
		}
		catch (InterruptedException ex) {
			Thread.currentThread().interrupt();
			return false;
		}
	}

	private void failed(int count, Exception cause) {
		metrics.errorCounter(FAILED_METRIC, count);
		alerts.error(OtlpOutput.class,
				"OTLP export to " + displayEndpoint + " failed; dropped " + count + " log records", cause);
	}

	private void partialSuccess(byte[] body, int count) {
		if (body.length == 0) {
			return;
		}
		long rejected;
		String message;
		if (protocol == OtlpProtocol.HTTP_PROTOBUF) {
			var decoded = PartialSuccess.decode(body);
			rejected = decoded.rejected();
			message = decoded.message();
		}
		else {
			var text = new String(body, StandardCharsets.UTF_8);
			var r = REJECTED_JSON.matcher(text);
			rejected = r.find() ? Long.parseLong(Objects.requireNonNullElse(r.group(1), "0")) : 0;
			var m = ERROR_JSON.matcher(text);
			message = m.find() ? Objects.requireNonNullElse(m.group(1), "") : "";
		}
		if (rejected > 0) {
			metrics.warnCounter(REJECTED_METRIC, rejected);
		}
		if (rejected > 0 || !message.isEmpty()) {
			alerts.warn(OtlpOutput.class, "OTLP endpoint " + displayEndpoint + " rejected " + rejected + " of " + count
					+ " log records" + (message.isEmpty() ? "" : ": " + message));
		}
	}

	private static String preview(byte[] body) {
		var s = new String(body, StandardCharsets.UTF_8);
		return s.length() > 200 ? s.substring(0, 200) + "..." : s;
	}

	/*
	 * Decodes ExportLogsServiceResponse.partial_success (field 1) holding
	 * rejected_log_records (1, varint) and error_message (2, string). Unknown fields are
	 * skipped.
	 */
	record PartialSuccess(long rejected, String message) {

		static PartialSuccess decode(byte[] body) {
			var outer = new ProtoReader(body, 0, body.length);
			long rejected = 0;
			String message = "";
			while (outer.hasMore()) {
				int tag = (int) outer.varint();
				if (tag >>> 3 == 1 && (tag & 7) == 2) {
					var inner = outer.message();
					while (inner.hasMore()) {
						int t = (int) inner.varint();
						if (t >>> 3 == 1 && (t & 7) == 0) {
							rejected = inner.varint();
						}
						else if (t >>> 3 == 2 && (t & 7) == 2) {
							var text = inner.message();
							message = new String(body, text.pos, text.end - text.pos, StandardCharsets.UTF_8);
						}
						else {
							inner.skip(t & 7);
						}
					}
				}
				else {
					outer.skip(tag & 7);
				}
			}
			return new PartialSuccess(rejected, message);
		}

	}

	private static final class ProtoReader {

		private final byte[] buf;

		int pos;

		private final int end;

		ProtoReader(byte[] buf, int pos, int end) {
			this.buf = buf;
			this.pos = pos;
			this.end = end;
		}

		boolean hasMore() {
			return pos < end;
		}

		long varint() {
			long result = 0;
			for (int shift = 0; shift < 64; shift += 7) {
				if (pos >= end) {
					throw new IllegalArgumentException("Truncated protobuf varint");
				}
				byte b = buf[pos++];
				if (shift == 63 && (b & 0xFE) != 0) {
					throw new IllegalArgumentException("Malformed protobuf varint");
				}
				result |= (long) (b & 0x7F) << shift;
				if ((b & 0x80) == 0) {
					return result;
				}
			}
			throw new IllegalArgumentException("Malformed protobuf varint");
		}

		ProtoReader message() {
			int length = length();
			var message = new ProtoReader(buf, pos, pos + length);
			pos += length;
			return message;
		}

		private int length() {
			long length = varint();
			if (length < 0 || length > end - pos) {
				throw new IllegalArgumentException("Invalid protobuf field length");
			}
			return (int) length;
		}

		private void advance(int length) {
			if (length > end - pos) {
				throw new IllegalArgumentException("Truncated protobuf field");
			}
			pos += length;
		}

		void skip(int wireType) {
			switch (wireType) {
				case 0 -> varint();
				case 1 -> advance(8);
				case 2 -> {
					int length = length();
					advance(length);
				}
				case 5 -> advance(4);
				default -> throw new IllegalArgumentException("Unsupported protobuf wire type: " + wireType);
			}
		}

	}

	private static final class ByteCapture implements LogOutput {

		final ByteArrayOutputStream bytes = new ByteArrayOutputStream();

		@Override
		public URI uri() {
			return URI.create("memory:///");
		}

		@Override
		public void write(LogEvent event, byte[] b, int off, int len, ContentType contentType) {
			bytes.write(b, off, len);
		}

		@Override
		public void flush() {
		}

		@Override
		public OutputType type() {
			return OutputType.MEMORY;
		}

		@Override
		public void close() {
		}

	}

	/*
	 * The output encodes whole batches itself, so the appender encoder only has to hand
	 * each event through.
	 */
	private static final class PassThroughEncoder implements LogEncoder {

		static final PassThroughEncoder INSTANCE = new PassThroughEncoder();

		private static final byte[] EMPTY = new byte[0];

		@Override
		public Buffer buffer(BufferHints hints) {
			return new Buffer() {
				@Override
				public void drain(LogOutput output, LogEvent event) {
					output.write(event, EMPTY, 0, 0, ContentType.StandardContentType.APPLICATION_JSON);
				}

				@Override
				public void clear() {
				}
			};
		}

		@Override
		public void encode(LogEvent event, Buffer buffer) {
		}

	}

}

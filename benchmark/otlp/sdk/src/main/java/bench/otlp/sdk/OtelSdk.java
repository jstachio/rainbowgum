package bench.otlp.sdk;

import java.util.concurrent.TimeUnit;

import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.exporter.otlp.http.logs.OtlpHttpLogRecordExporter;
import io.opentelemetry.sdk.OpenTelemetrySdk;
import io.opentelemetry.sdk.logs.SdkLoggerProvider;
import io.opentelemetry.sdk.logs.export.BatchLogRecordProcessor;
import io.opentelemetry.sdk.resources.Resource;

/**
 * The OpenTelemetry SDK setup the Logback and Log4j2 appenders hand their events to:
 * OTLP/HTTP protobuf with the JDK HTTP sender, through the SDK's batch processor with its
 * defaults (queue 2048, batch 512, 1 s delay), which drops records when the queue is full.
 * {@code bench.otel.maxQueueSize} overrides the queue size.
 */
public final class OtelSdk {

	private final OpenTelemetrySdk sdk;

	private final SdkLoggerProvider provider;

	private OtelSdk() {
		var exporter = OtlpHttpLogRecordExporter.builder()
			.setEndpoint(System.getProperty("bench.endpoint", "http://localhost:4318/v1/logs"))
			.build();
		var processor = BatchLogRecordProcessor.builder(exporter);
		Integer queue = Integer.getInteger("bench.otel.maxQueueSize");
		if (queue != null) {
			processor.setMaxQueueSize(queue);
		}
		this.provider = SdkLoggerProvider.builder()
			.setResource(Resource.getDefault()
				.merge(Resource.create(Attributes.builder().put("service.name", "otlp-benchmark").build())))
			.addLogRecordProcessor(processor.build())
			.build();
		this.sdk = OpenTelemetrySdk.builder().setLoggerProvider(provider).build();
	}

	public static OtelSdk create() {
		return new OtelSdk();
	}

	public OpenTelemetrySdk sdk() {
		return sdk;
	}

	public void flush() {
		provider.forceFlush().join(30, TimeUnit.SECONDS);
	}

}

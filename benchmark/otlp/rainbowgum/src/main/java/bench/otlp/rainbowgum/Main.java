package bench.otlp.rainbowgum;

import org.slf4j.LoggerFactory;

import bench.otlp.OtlpBenchmark;

/*
 * rainbowgum-otlp output with the async publisher, as the user guide recommends. The OTLP
 * output batches up to 512 records per request and sends protobuf with the JDK client.
 * Thread attributes and every MDC key value are always sent, so the OpenTelemetry appenders
 * are configured to send the same.
 */
public class Main {

	public static void main(String[] args) throws Exception {
		setDefault("logging.appenders", "otel");
		setDefault("logging.appender.otel.output.uri", "otlp:///");
		setDefault("logging.appender.otel.output.otlp.endpoint",
				System.getProperty("bench.endpoint", "http://localhost:4318/v1/logs"));
		setDefault("logging.appender.otel.output.otlp.serviceName", "otlp-benchmark");
		setDefault("logging.route.default.publisher.uri", "async");
		var log = LoggerFactory.getLogger("bench.otlp.Orders");
		// The async publisher flushes the output after each batch it drains.
		OtlpBenchmark.run("rainbowgum", log, () -> {
		});
	}

	private static void setDefault(String key, String value) {
		if (System.getProperty(key) == null) {
			System.setProperty(key, value);
		}
	}

}

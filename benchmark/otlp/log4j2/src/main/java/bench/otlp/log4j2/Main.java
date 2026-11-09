package bench.otlp.log4j2;

import org.slf4j.LoggerFactory;

import bench.otlp.OtlpBenchmark;
import bench.otlp.sdk.OtelSdk;
import io.opentelemetry.instrumentation.log4j.appender.v2_17.OpenTelemetryAppender;

/*
 * The OpenTelemetry Log4j2 appender from log4j2.xml, through Log4j2's SLF4J binding so the
 * call sites match the other frameworks.
 */
public class Main {

	public static void main(String[] args) throws Exception {
		var log = LoggerFactory.getLogger("bench.otlp.Orders");
		var otel = OtelSdk.create();
		OpenTelemetryAppender.install(otel.sdk());
		OtlpBenchmark.run("log4j2", log, otel::flush);
		otel.sdk().close();
	}

}

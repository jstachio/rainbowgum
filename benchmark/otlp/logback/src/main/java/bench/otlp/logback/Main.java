package bench.otlp.logback;

import org.slf4j.LoggerFactory;

import bench.otlp.OtlpBenchmark;
import bench.otlp.sdk.OtelSdk;
import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.LoggerContext;
import io.opentelemetry.instrumentation.logback.appender.v1_0.OpenTelemetryAppender;

/*
 * The OpenTelemetry Logback appender, configured in code as its README's logback.xml
 * would: capture thread attributes and every MDC key, to match what Rainbow Gum sends.
 */
public class Main {

	public static void main(String[] args) throws Exception {
		var context = (LoggerContext) LoggerFactory.getILoggerFactory();
		context.reset();
		var appender = new OpenTelemetryAppender();
		appender.setContext(context);
		appender.setName("otel");
		appender.setCaptureExperimentalAttributes(true);
		appender.setMdcAttributesIncluded("*");
		appender.start();
		var root = context.getLogger(org.slf4j.Logger.ROOT_LOGGER_NAME);
		root.setLevel(Level.INFO);
		root.addAppender(appender);

		var otel = OtelSdk.create();
		OpenTelemetryAppender.install(otel.sdk());
		OtlpBenchmark.run("logback", LoggerFactory.getLogger("bench.otlp.Orders"), otel::flush);
		otel.sdk().close();
	}

}

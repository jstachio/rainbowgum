/**
 * <strong>EXPERIMENTAL</strong> Adds the trace and span id of the current OpenTelemetry span
 * to every event as the key values {@code traceId} and {@code spanId}, the names Micrometer
 * Tracing uses and the OTLP output reads. Simply being on the classpath is enough: the
 * span is read when the event is created, on the logging thread, from whatever set the
 * OpenTelemetry context (the Java agent, the SDK, or a bridge), with no MDC involved.
 *
 * @provides io.jstach.rainbowgum.spi.RainbowGumServiceProvider
 */
module io.jstach.rainbowgum.opentelemetry {

	exports io.jstach.rainbowgum.opentelemetry;

	requires io.jstach.rainbowgum;
	requires io.opentelemetry.api;

	requires static org.jspecify;
	requires static io.jstach.svc;

	provides io.jstach.rainbowgum.spi.RainbowGumServiceProvider
			with io.jstach.rainbowgum.opentelemetry.OpenTelemetryConfigurator;

}

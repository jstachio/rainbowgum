/**
 * OpenTelemetry Protocol (OTLP) log export without the OpenTelemetry SDK or any protobuf
 * library: an OTLP/JSON lines encoder and an OTLP/HTTP output that sends either
 * protobuf or JSON.
 *
 * @provides io.jstach.rainbowgum.spi.RainbowGumServiceProvider
 */
module io.jstach.rainbowgum.otlp {

	exports io.jstach.rainbowgum.otlp;

	requires transitive io.jstach.rainbowgum;
	requires io.jstach.rainbowgum.json;
	requires java.net.http;

	requires static io.jstach.rainbowgum.annotation;
	requires static io.jstach.svc;
	requires static org.jspecify;

	provides io.jstach.rainbowgum.spi.RainbowGumServiceProvider with io.jstach.rainbowgum.otlp.OtlpConfigurator;

}

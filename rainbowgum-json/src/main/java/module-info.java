/**
 * Provides JSON encoders.
 * This module does not require external JSON libraries but instead
 * provides a zero dependency JSON writer.
 * @see io.jstach.rainbowgum.json.encoder
 * @see io.jstach.rainbowgum.json.JsonBuffer
 * @see io.jstach.rainbowgum.json.encoder.GelfEncoder
 * @see io.jstach.rainbowgum.json.encoder.EcsEncoder
 * @see io.jstach.rainbowgum.json.encoder.LogstashEncoder
 */
module io.jstach.rainbowgum.json {
	exports io.jstach.rainbowgum.json;
	exports io.jstach.rainbowgum.json.encoder;
	requires transitive io.jstach.rainbowgum;

	requires static io.jstach.rainbowgum.annotation;
	requires static io.jstach.svc;
	requires static org.eclipse.jdt.annotation;

	provides io.jstach.rainbowgum.spi.RainbowGumServiceProvider
		with io.jstach.rainbowgum.json.encoder.GelfEncoderConfigurator,
			io.jstach.rainbowgum.json.encoder.EcsEncoderConfigurator,
			io.jstach.rainbowgum.json.encoder.LogstashEncoderConfigurator;
}
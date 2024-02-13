/**
 * Provides JSON encoders.
 * This module does not require external JSON libraries but instead
 * provides a zero dependency JSON writer.
 */
module io.jstach.rainbowgum.json {
	exports io.jstach.rainbowgum.json;
	exports io.jstach.rainbowgum.json.encoder;
	requires transitive io.jstach.rainbowgum;
	
	requires static io.jstach.rainbowgum.annotation;
	requires static io.jstach.svc;
	requires static org.eclipse.jdt.annotation;
	
	provides io.jstach.rainbowgum.spi.RainbowGumServiceProvider 
		with io.jstach.rainbowgum.json.encoder.GelfEncoderConfigurator;
}
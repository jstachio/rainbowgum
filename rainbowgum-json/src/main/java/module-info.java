/**
 * Provides JSON encoders.
 * This module does not require external JSON libraries but instead
 * provides a zero dependency JSON writer.
 */
module io.jstach.rainbowgum.json {
	exports io.jstach.rainbowgum.json;
	exports io.jstach.rainbowgum.json.encoder;
	requires transitive io.jstach.rainbowgum;
	
	requires static org.eclipse.jdt.annotation;
}
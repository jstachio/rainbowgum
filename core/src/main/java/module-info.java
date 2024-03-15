/**
 * Core module for RainbowGum which provides low level components for logging as well as a builder 
 * for creating custom RainbowGums. Use {@link io.jstach.rainbowgum.RainbowGum#builder()} to
 * get starting configuring.
 * 
 * @uses io.jstach.rainbowgum.spi.RainbowGumServiceProvider
 * @see io.jstach.rainbowgum
 */

module io.jstach.rainbowgum {
	exports io.jstach.rainbowgum;
	exports io.jstach.rainbowgum.format;
	exports io.jstach.rainbowgum.output;
	exports io.jstach.rainbowgum.spi;
	
	exports io.jstach.rainbowgum.internal to io.jstach.rainbowgum.slf4j;
	
	requires static io.jstach.rainbowgum.annotation;
	requires static org.eclipse.jdt.annotation;
	
	uses io.jstach.rainbowgum.spi.RainbowGumServiceProvider;
}
/**
 * Core module for RainbowGum which provides low level components for logging as well as a builder 
 * for creating custom RainbowGums. Use {@link io.jstach.rainbowgum.RainbowGum#builder()} to
 * get starting configuring.
 * 
 * @see io.jstach.rainbowgum
 * @uses io.jstach.rainbowgum.spi.RainbowGumServiceProvider
 */

module io.jstach.rainbowgum {
	exports io.jstach.rainbowgum;
	exports io.jstach.rainbowgum.format;
	exports io.jstach.rainbowgum.output;
	exports io.jstach.rainbowgum.spi;
	
	requires static io.jstach.rainbowgum.annotation;
	requires static org.eclipse.jdt.annotation;
	
	uses io.jstach.rainbowgum.spi.RainbowGumServiceProvider;
}
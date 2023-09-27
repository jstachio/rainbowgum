module io.jstach.rainbowgum {
	exports io.jstach.rainbowgum;
	exports io.jstach.rainbowgum.json;
	exports io.jstach.rainbowgum.format;
	exports io.jstach.rainbowgum.spi;
	
	requires static org.eclipse.jdt.annotation;
	
	uses io.jstach.rainbowgum.spi.RainbowGumServiceProvider;
}
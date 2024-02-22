import io.jstach.rainbowgum.spi.RainbowGumServiceProvider;

/**
 * Provides Logback style pattern formatters.
 * @provides RainbowGumServiceProvider
 */
module io.jstach.rainbowgum.pattern {
	
	exports io.jstach.rainbowgum.pattern;
	exports io.jstach.rainbowgum.pattern.format;
	
	requires transitive io.jstach.rainbowgum;
	
	requires static io.jstach.rainbowgum.annotation;
	requires static io.jstach.svc;
	requires static org.eclipse.jdt.annotation;
	
	provides RainbowGumServiceProvider with io.jstach.rainbowgum.pattern.format.PatternConfigurator;
	
}
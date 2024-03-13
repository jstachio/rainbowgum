import io.jstach.rainbowgum.spi.RainbowGumServiceProvider;

/**
 * Provides 
 * <a href="https://logback.qos.ch/manual/layouts.html#ClassicPatternLayout">Logback style pattern formatters.</a>
 * The URI scheme of pattern encoders is {@value io.jstach.rainbowgum.pattern.format.PatternEncoder#PATTERN_SCHEME}.
 * 
 * @provides RainbowGumServiceProvider
 * @see io.jstach.rainbowgum.pattern.format.PatternConfigurator
 * @see io.jstach.rainbowgum.pattern.format.PatternEncoder
 */
module io.jstach.rainbowgum.pattern {
	
	exports io.jstach.rainbowgum.pattern;
	exports io.jstach.rainbowgum.pattern.format;
	exports io.jstach.rainbowgum.pattern.format.spi;

	requires transitive io.jstach.rainbowgum;
	
	requires static io.jstach.rainbowgum.annotation;
	requires static io.jstach.svc;
	requires static org.eclipse.jdt.annotation;
	
	provides RainbowGumServiceProvider with io.jstach.rainbowgum.pattern.format.PatternConfigurator;
	
}
import io.jstach.rainbowgum.spi.RainbowGumServiceProvider;

/**
 * Provides 
 * <a href="https://logback.qos.ch/manual/layouts.html#ClassicPatternLayout">Logback style pattern formatters.</a>
 * The URI scheme of pattern encoders is {@value io.jstach.rainbowgum.pattern.format.PatternEncoder#PATTERN_SCHEME}.
 * <p>
 * The supported builtin keywords are in the follow enum types:
 * <ul>
 * <li>{@link io.jstach.rainbowgum.pattern.format.PatternRegistry.KeywordKey}</li>
 * <li>{@link io.jstach.rainbowgum.pattern.format.PatternRegistry.ColorKey}</li>
 * </ul>
 * <strong>Rainbow Gum does not currently support all of the builtin keywords that Logback does!</strong>
 * But most of them are available.
 * 
 * @provides RainbowGumServiceProvider
 * @see io.jstach.rainbowgum.pattern.format.PatternConfigurator
 * @see io.jstach.rainbowgum.pattern.format.PatternEncoder
 * @see io.jstach.rainbowgum.pattern.format.PatternConfig
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
import io.jstach.rainbowgum.slf4j.RainbowGumSLF4JServiceProvider;

/**
 * <a href="https://slf4j.org">SLF4J</a> 2.0 implementation.
 * <p>
 * Rainbow Gum SLF4J implementation supports:
 * <ul>
 * <li><a href="https://slf4j.org/manual.html#mdc">MDC</a></li>
 * <li><a href="https://slf4j.org/manual.html#fluent">Fluent Logging API</a></li>
 * <li>{@linkplain io.jstach.rainbowgum.slf4j.spi.LoggerDecoratorService Decorating loggers}</li>
 * <li>{@linkplain io.jstach.rainbowgum.LogEvent.Caller Caller Information}<li>
 * </ul>
 * However there is currently no {@link org.slf4j.Marker}
 * support. Furthermore the key value pair support
 * in {@link org.slf4j.spi.LoggingEventBuilder} works by overlaying on top
 * of the current MDC at the time the event is constructed and put into the
 * {@link io.jstach.rainbowgum.LogEvent#keyValues() events key values}.
 * <p>
 * Rainbow Gum SLF4J implementation is unique in that it has 
 * two special implementation of loggers.
 * <ul>
 * <li>Level Logger - logger based on level threshold and <em>can never change</em>!</li>
 * <li>Changing Logger - level and other configuration <em>can change</em>.</li>
 * </ul>
 * Other looging implementations like Logback by default use something analogous to changing loggers
 * which require a constant check if the level threshold has changed.
 * Level loggers do not need to that check. Unless changing loggers is turned on by default Level 
 * Loggers are used which are close to zero cost for discarding events.
 * 
 * @provides org.slf4j.spi.SLF4JServiceProvider
 */
module io.jstach.rainbowgum.slf4j {
	
	exports io.jstach.rainbowgum.slf4j;
	exports io.jstach.rainbowgum.slf4j.spi;

	requires static org.eclipse.jdt.annotation;
	requires static io.jstach.svc;
	
	requires transitive org.slf4j;
	requires transitive io.jstach.rainbowgum;
	
	provides org.slf4j.spi.SLF4JServiceProvider with RainbowGumSLF4JServiceProvider;
}
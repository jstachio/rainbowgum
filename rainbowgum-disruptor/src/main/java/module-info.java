/**
 * <strong>EXPERIMENTAL</strong> Disruptor async publisher.
 * This optional module will install Disruptor as the default
 * async publisher provider.
 * <p>
 * Maven dependency:
 * {@snippet lang=xml :
 * <dependency>
 *     <groupId>io.jstach.rainbowgum</groupId>
 *     <artifactId>rainbowgum-disruptor</artifactId>
 *     <version>_VERSION_</version>
 * </dependency>
 * }
 */

module io.jstach.rainbowgum.disruptor {
	
	exports io.jstach.rainbowgum.disruptor;
	
	requires transitive io.jstach.rainbowgum;
	requires static io.jstach.rainbowgum.annotation;
	requires com.lmax.disruptor;
	requires static org.jspecify;
	requires static io.jstach.svc;
	
	provides io.jstach.rainbowgum.spi.RainbowGumServiceProvider 
		with io.jstach.rainbowgum.disruptor.DisruptorConfigurator;
}
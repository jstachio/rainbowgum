/**
 * <strong>EXPERIMENTAL</strong> Disruptor async publisher.
 */

module io.jstach.rainbowgum.disruptor {
	
	exports io.jstach.rainbowgum.disruptor;
	
	requires transitive io.jstach.rainbowgum;
	requires static io.jstach.rainbowgum.annotation;
	requires com.lmax.disruptor;
	requires static org.eclipse.jdt.annotation;
	requires static io.jstach.svc;
	
	provides io.jstach.rainbowgum.spi.RainbowGumServiceProvider 
		with io.jstach.rainbowgum.disruptor.DisruptorConfigurator;
}
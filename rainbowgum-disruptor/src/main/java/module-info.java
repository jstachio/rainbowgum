/**
 * Disruptor async publisher
 */
module io.jstach.rainbowgum.disruptor {
	
	exports io.jstach.rainbowgum.disruptor;
	
	requires transitive io.jstach.rainbowgum;
	requires com.lmax.disruptor;
	requires static org.eclipse.jdt.annotation;
}
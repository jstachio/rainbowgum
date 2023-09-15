module io.jstach.rainbowgum {
	exports io.jstach.rainbowgum;
	exports io.jstach.rainbowgum.jansi;
	exports io.jstach.rainbowgum.json;
	
	requires static org.eclipse.jdt.annotation;
	requires org.fusesource.jansi;
	requires com.lmax.disruptor;
	
	uses io.jstach.rainbowgum.RainbowGum;
}
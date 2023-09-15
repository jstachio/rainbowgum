import io.jstach.rainbowgum.slf4j.RainbowGumSLF4JServiceProvider;

module io.jstach.rainbowgum.slf4j {
	
	requires static org.eclipse.jdt.annotation;
	requires static io.jstach.svc;
	
	requires org.slf4j;
	requires io.jstach.rainbowgum;
	
	provides org.slf4j.spi.SLF4JServiceProvider with RainbowGumSLF4JServiceProvider;
}
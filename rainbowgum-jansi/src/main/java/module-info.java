import io.jstach.rainbowgum.spi.RainbowGumServiceProvider;

/**
 * JANSI module that will install jansi.
 * 
 * @provides RainbowGumServiceProvider
 */
module io.jstach.rainbowgum.jansi {
	
	exports io.jstach.rainbowgum.jansi;
	
	requires transitive io.jstach.rainbowgum;
	requires org.fusesource.jansi;
	
	requires static io.jstach.svc;
	requires static org.eclipse.jdt.annotation;
	
	provides RainbowGumServiceProvider with io.jstach.rainbowgum.jansi.JansiInitializer;
}
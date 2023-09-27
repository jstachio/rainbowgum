import io.jstach.rainbowgum.spi.RainbowGumServiceProvider;

module io.jstach.rainbowgum.jansi {
	requires transitive io.jstach.rainbowgum;
	requires org.fusesource.jansi;
	
	requires static io.jstach.svc;
	requires static org.eclipse.jdt.annotation;
	
	provides RainbowGumServiceProvider with io.jstach.rainbowgum.jansi.JansiInitializer;
}
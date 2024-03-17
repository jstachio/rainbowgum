import io.jstach.rainbowgum.spi.RainbowGumServiceProvider;

/**
 * JANSI module that will install Jansi.
 * <p>
 * Jansi can be disabled either with 
 * {@value io.jstach.rainbowgum.LogProperties#GLOBAL_ANSI_DISABLE_PROPERTY} set to <code>true</code>
 * or {@value io.jstach.rainbowgum.jansi.JAnsiConfigurator#JANSI_DISABLE} set to <code>true</code>.
 * <p>
 * The {@link io.jstach.rainbowgum.jansi.JAnsiConfigurator#JANSI_DISABLE} property is useful
 * to set in development environments particularly IntelliJ which Jansi does not currently 
 * detect as ANSI escape supported (and therefore will strip the ANSI escape sequences).
 * 
 * @provides RainbowGumServiceProvider
 */
module io.jstach.rainbowgum.jansi {
	
	exports io.jstach.rainbowgum.jansi;
	
	requires transitive io.jstach.rainbowgum;
	requires org.fusesource.jansi;
	
	requires static io.jstach.svc;
	requires static org.eclipse.jdt.annotation;
	
	provides RainbowGumServiceProvider with io.jstach.rainbowgum.jansi.JAnsiConfigurator;
}
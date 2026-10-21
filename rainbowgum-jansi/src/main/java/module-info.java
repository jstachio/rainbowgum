import io.jstach.rainbowgum.spi.RainbowGumServiceProvider;

/**
 * JANSI module that will install Jansi.
 * <p>
 * Jansi can be disabled either with 
 * {@value io.jstach.rainbowgum.LogProperties#GLOBAL_ANSI_MODE_PROPERTY} set to <code>off</code>
 * or {@value io.jstach.rainbowgum.jansi.JAnsiConfigurator#JANSI_DISABLE} set to <code>true</code>.
 * <p>
 * The {@link io.jstach.rainbowgum.jansi.JAnsiConfigurator#JANSI_DISABLE} property is useful
 * to set in development environments particularly IntelliJ which Jansi does not currently 
 * detect as ANSI escape supported (and therefore will strip the ANSI escape sequences).
 * 
 * @provides RainbowGumServiceProvider
 */
@io.jstach.rainbowgum.annotation.MavenModule(groupId = "io.jstach.rainbowgum", artifactId = "rainbowgum-jansi")
module io.jstach.rainbowgum.jansi {
	requires static io.jstach.rainbowgum.annotation;
	
	exports io.jstach.rainbowgum.jansi;

	requires transitive io.jstach.rainbowgum;
	requires org.fusesource.jansi;
	
	requires static io.jstach.svc;
	requires static org.jspecify;
	
	provides RainbowGumServiceProvider with io.jstach.rainbowgum.jansi.JAnsiConfigurator;
}
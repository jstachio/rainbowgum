import io.avaje.applog.AppLog;
import io.jstach.rainbowgum.spi.RainbowGumServiceProvider;

/**
 * Uses <a href="https://avaje.io/config/">Avaje Config</a> for LogProperties.
 * @provides RainbowGumServiceProvider
 * @provides AppLog.Provider
 */
@io.jstach.rainbowgum.annotation.MavenModule(groupId = "io.jstach.rainbowgum", artifactId = "rainbowgum-avaje-config")
module io.jstach.rainbowgum.avaje {
	requires static io.jstach.rainbowgum.annotation;
	
	exports io.jstach.rainbowgum.avaje;
	
	requires transitive io.jstach.rainbowgum;
	requires io.jstach.rainbowgum.systemlogger;
	requires io.avaje.applog;
	requires io.avaje.config;
	
	requires static io.jstach.svc;
	requires static org.jspecify;
	
	provides RainbowGumServiceProvider with io.jstach.rainbowgum.avaje.AvajePropertiesProvider;
	provides AppLog.Provider with io.jstach.rainbowgum.avaje.RainbowGumAppLog;
}
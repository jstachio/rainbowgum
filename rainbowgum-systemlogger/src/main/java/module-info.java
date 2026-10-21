/**
 * This module provides a partial System.Logger implementation. 
 * <strong>
 * It DOES NOT PROVIDE A  {@link java.lang.System.LoggerFinder} implementation
 * and thus safe to mix in with other libraries that do.
 * </strong>.
 *  
 */
@io.jstach.rainbowgum.annotation.MavenModule(groupId = "io.jstach.rainbowgum", artifactId = "rainbowgum-systemlogger")
module io.jstach.rainbowgum.systemlogger {
	requires static io.jstach.rainbowgum.annotation;
	exports io.jstach.rainbowgum.systemlogger;
	requires transitive io.jstach.rainbowgum;
	requires static org.jspecify;
}
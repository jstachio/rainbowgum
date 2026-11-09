/**
 * This module provides a partial System.Logger implementation. 
 * <strong>
 * It DOES NOT PROVIDE A  {@link java.lang.System.LoggerFinder} implementation
 * and thus safe to mix in with other libraries that do.
 * </strong>.
 * <p>
 * Maven dependency:
 * {@snippet lang=xml :
 * <dependency>
 *     <groupId>io.jstach.rainbowgum</groupId>
 *     <artifactId>rainbowgum-systemlogger</artifactId>
 *     <version>_VERSION_</version>
 * </dependency>
 * }
 *  
 */
module io.jstach.rainbowgum.systemlogger {
	exports io.jstach.rainbowgum.systemlogger;
	requires transitive io.jstach.rainbowgum;
	requires static org.jspecify;
}
/**
 * This module provides a partial System.Logger implementation. 
 * <strong>
 * It DOES NOT PROVIDE A  {@link java.lang.System.LoggerFinder} implementation
 * and thus safe to mix in with other libraries that do.
 * </strong>.
 *  
 */
module io.jstach.rainbowgum.systemlogger {
	exports io.jstach.rainbowgum.systemlogger;
	requires transitive io.jstach.rainbowgum;
	requires static org.eclipse.jdt.annotation;
}
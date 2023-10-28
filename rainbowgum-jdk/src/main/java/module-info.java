/**
 * Rainbowgum JDK components
 * @provides System.LoggerFinder
 */
module io.jstach.rainbowgum.jdk {
	
	exports io.jstach.rainbowgum.jul;
	
	requires io.jstach.rainbowgum;
	requires java.logging;
	requires static org.eclipse.jdt.annotation;
	requires static io.jstach.svc;
	
	provides System.LoggerFinder with io.jstach.rainbowgum.systemlogger.SystemLoggingFactory;
}
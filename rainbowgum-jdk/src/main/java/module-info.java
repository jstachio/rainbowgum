module io.jstach.rainbowgum.jdk {
	requires io.jstach.rainbowgum;
	requires java.logging;
	requires static org.eclipse.jdt.annotation;
	requires static io.jstach.svc;
	
	provides System.LoggerFinder with io.jstach.rainbowgum.systemlogger.SystemLoggingFactory;
}
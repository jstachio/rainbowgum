/**
 * Rainbow Gum Tomcat logging implementation usually for Spring Boot.
 * @provides org.apache.juli.logging.Log
 */
module io.jstach.rainbowgum.tomcat {
	requires io.jstach.rainbowgum;
	requires static org.apache.tomcat.juli;
	requires static org.eclipse.jdt.annotation;
	requires static io.jstach.svc;
	
	provides org.apache.juli.logging.Log with io.jstach.rainbowgum.tomcat.RainbowGumTomcatLog;
}
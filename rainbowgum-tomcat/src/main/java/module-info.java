/**
 * Rainbow Gum Tomcat logging implementation usually for Spring Boot.
 * @provides org.apache.juli.logging.Log
 */
@io.jstach.rainbowgum.annotation.MavenModule(groupId = "io.jstach.rainbowgum", artifactId = "rainbowgum-tomcat")
module io.jstach.rainbowgum.tomcat {
	requires static io.jstach.rainbowgum.annotation;
	requires io.jstach.rainbowgum;
	requires static org.apache.tomcat.juli;
	requires static org.jspecify;
	requires static io.jstach.svc;
	
	provides org.apache.juli.logging.Log with io.jstach.rainbowgum.tomcat.RainbowGumTomcatLog;
}
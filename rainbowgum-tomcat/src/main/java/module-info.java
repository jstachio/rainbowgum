/**
 * Rainbow Gum Tomcat logging implementation usually for Spring Boot.
 * <p>
 * Maven dependency:
 * {@snippet lang=xml :
 * <dependency>
 *     <groupId>io.jstach.rainbowgum</groupId>
 *     <artifactId>rainbowgum-tomcat</artifactId>
 *     <version>_VERSION_</version>
 * </dependency>
 * }
 * @provides org.apache.juli.logging.Log
 */
module io.jstach.rainbowgum.tomcat {
	requires io.jstach.rainbowgum;
	requires static org.apache.tomcat.juli;
	requires static org.jspecify;
	requires static io.jstach.svc;
	
	provides org.apache.juli.logging.Log with io.jstach.rainbowgum.tomcat.RainbowGumTomcatLog;
}
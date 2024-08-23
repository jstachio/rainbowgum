/**
 * Spring Boot Rainbow Gum Integration.
 * Please refer to the overview for installation.
 */
module io.jstach.rainbowgum.spring.boot.starter {
	requires static org.eclipse.jdt.annotation;
	/*
	 * TODO For some reason Maven javadoc plugin thinks
	 * this is needed.
	 */
	requires io.jstach.rainbowgum.spring.boot;
}
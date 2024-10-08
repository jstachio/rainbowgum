/**
 * Rainbow Gum Spring Boot integration.
 */
module io.jstach.rainbowgum.spring.boot {
	exports io.jstach.rainbowgum.spring.boot.spi;
	requires transitive io.jstach.rainbowgum;
	requires io.jstach.rainbowgum.pattern;
	
	/*
	 * Note that we never require transitive of spring stuff
	 * as it is an automatic module.
	 */
	requires spring.boot;
	requires spring.core;
	requires static org.eclipse.jdt.annotation;
	requires static io.jstach.svc;
	
	/*
	 * Spring does not need this because it is an automatic module
	 * but in theory some day they will as the boot package
	 * is not exported or open.
	 */
	provides org.springframework.boot.logging.LoggingSystemFactory 
		with io.jstach.rainbowgum.spring.boot.RainbowGumLoggingSystemFactory;
	
	provides io.jstach.rainbowgum.spi.RainbowGumServiceProvider
		with io.jstach.rainbowgum.spring.boot.PreBootRainbowGumProvider;
	
	uses io.jstach.rainbowgum.spring.boot.spi.SpringRainbowGumServiceProvider;
}
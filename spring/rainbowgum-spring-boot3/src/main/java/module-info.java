/**
 * Rainbow Gum Spring Boot 3 integration.
 * <p>
 * Both the module name and the {@code io.jstach.rainbowgum.spring.boot3} package are
 * suffixed with the Spring Boot major version because this module's code is duplicated
 * (not shared) with {@code io.jstach.rainbowgum.spring.boot4}: none of it is exported, all
 * of it is wired up internally through {@code provides}/{@code uses} or Spring Boot's own
 * {@code META-INF/spring.factories}, so no consumer ever imports it directly and there is
 * no "drop-in" benefit to giving the two modules' internals the same package name - unlike
 * two modules ever sharing a module <em>name</em>, which is a hard JPMS conflict the moment
 * both are combined onto one module graph, as this repository's own aggregate javadoc build
 * does. The SPI package ({@code io.jstach.rainbowgum.spring.boot.spi}) lives in its own
 * genuinely shared module, {@code io.jstach.rainbowgum.spring.boot.spi}, since it does not
 * depend on Spring Boot at all (just the stable {@code Environment} type from spring-core)
 * and is meant to be implemented by consumers, so it is neither duplicated nor suffixed.
 */
module io.jstach.rainbowgum.spring.boot3 {
	requires transitive io.jstach.rainbowgum;
	requires transitive io.jstach.rainbowgum.spring.boot.spi;
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
		with io.jstach.rainbowgum.spring.boot3.RainbowGumLoggingSystemFactory;

	provides io.jstach.rainbowgum.spi.RainbowGumServiceProvider
		with io.jstach.rainbowgum.spring.boot3.PreBootRainbowGumProvider;

	uses io.jstach.rainbowgum.spring.boot.spi.SpringRainbowGumServiceProvider;
}

/**
 * Rainbow Gum Spring SPI: shared by {@code io.jstach.rainbowgum.spring.boot3} and
 * {@code io.jstach.rainbowgum.spring.boot4}.
 * <p>
 * This module and the package it exports are deliberately not suffixed with a Spring Boot
 * major version, unlike the two modules above: it depends only on
 * {@code org.springframework.core.env.Environment} (spring-core, not spring-boot), which is
 * expected to remain source and binary compatible across Spring Boot major versions, so
 * there is no reason to force implementers of {@code SpringRainbowGumServiceProvider} to
 * change their import when bumping Spring Boot major versions.
 */
module io.jstach.rainbowgum.spring.boot.spi {
	exports io.jstach.rainbowgum.spring.boot.spi;
	requires transitive io.jstach.rainbowgum;

	/*
	 * Note that we never require transitive of spring stuff
	 * as it is an automatic module.
	 */
	requires spring.core;
	requires static org.eclipse.jdt.annotation;
}

/**
 * Rainbowgum Annotations used for code generation.
 * This module is only needed for code generation annotations namely builders driven by configuration.
 * It is separated from core so that code generation can be done on the core module.
 * <p>
 * Maven dependency:
 * {@snippet lang=xml :
 * <dependency>
 *     <groupId>io.jstach.rainbowgum</groupId>
 *     <artifactId>rainbowgum-annotation</artifactId>
 *     <version>_VERSION_</version>
 * </dependency>
 * }
 */
module io.jstach.rainbowgum.annotation {
	exports io.jstach.rainbowgum.annotation;
}
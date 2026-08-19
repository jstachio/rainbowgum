/**
 * Spring Rainbow Gum SPI. <strong> This SPI does not use the normal service loader but
 * Spring Boot factories loader. </strong>
 * <p>
 * This package lives in its own module ({@code io.jstach.rainbowgum.spring.boot.spi})
 * that is shared by both {@code io.jstach.rainbowgum.spring.boot3} and
 * {@code io.jstach.rainbowgum.spring.boot4}: it only depends on
 * {@code org.springframework.core.env.Environment}, which is expected to remain stable
 * across Spring Boot major versions, so implementers of
 * {@link io.jstach.rainbowgum.spring.boot.spi.SpringRainbowGumServiceProvider} never need
 * to change this import when bumping Spring Boot major versions.
 */
@org.eclipse.jdt.annotation.NonNullByDefault
package io.jstach.rainbowgum.spring.boot.spi;

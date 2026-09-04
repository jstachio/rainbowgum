/**
 * Rainbow Gum Spring Boot 4 Actuator/Micrometer integration.
 * <p>
 * See {@code io.jstach.rainbowgum.spring.boot4}'s module-info for why the module name is
 * suffixed with the Spring Boot major version while the packages are not, and why this
 * is deliberately its own module rather than folded into
 * {@code io.jstach.rainbowgum.spring.boot4} itself: Micrometer/Actuator are an opt in
 * dependency, not something every RainbowGum + Spring Boot user should be forced onto.
 */
module io.jstach.rainbowgum.spring.boot4.actuator {
	requires transitive io.jstach.rainbowgum;

	requires spring.boot.autoconfigure;
	requires spring.boot.micrometer.metrics;
	requires spring.context;
	requires micrometer.core;

	requires static org.eclipse.jdt.annotation;

	// Spring reflectively instantiates the auto configuration class and, since
	// @AutoConfiguration defaults to full (CGLIB proxied) @Configuration mode,
	// ConfigurationClassEnhancer (in spring.context) reflectively sets a field on the
	// generated subclass. Left unqualified rather than naming just spring.context - which
	// exact Spring module needs access has already changed once between what seemed like
	// the obvious guess (spring.core, where CGLIB itself lives) and the module actually
	// doing the reflective field access, so pinning to one risks the same break again on
	// a future Spring version.
	opens io.jstach.rainbowgum.spring.boot4.actuator;
}

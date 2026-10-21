package io.jstach.rainbowgum.annotation;

import static java.lang.annotation.ElementType.MODULE;
import static java.lang.annotation.RetentionPolicy.SOURCE;

import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;

// @formatter:off
/**
 * Documents the Maven coordinates of a Java module on its {@code module-info.java}
 * declaration. The group and artifact identify the dependency that provides the module,
 * whose Java module name can differ from its Maven artifact name.
 * <p>
 * For example:
 * {@snippet :
 * @io.jstach.rainbowgum.annotation.MavenModule(
 *     groupId = "io.jstach.rainbowgum", artifactId = "rainbowgum-core")
 * module io.jstach.rainbowgum {
 *     requires static io.jstach.rainbowgum.annotation;
 * }
 * }
 * <p>
 * This annotation appears in source-generated Javadoc and is not retained in the compiled
 * module descriptor. Other modules need only a compile-time dependency on the annotation
 * module, declared with {@code requires static}.
 */
// @formatter:on
@Documented
@Retention(SOURCE)
@Target(MODULE)
public @interface MavenModule {

	/**
	 * The Maven group ID.
	 * @return group ID, for example {@code io.jstach.rainbowgum}.
	 */
	String groupId();

	/**
	 * The Maven artifact ID.
	 * @return artifact ID, for example {@code rainbowgum-core}.
	 */
	String artifactId();

}

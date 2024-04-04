package io.jstach.rainbowgum.annotation;

import static java.lang.annotation.ElementType.METHOD;
import static java.lang.annotation.ElementType.TYPE;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Indicates a class that was copied from another trusted open source project and does not
 * need test coverage.
 *
 * @hidden
 */
@Retention(RetentionPolicy.RUNTIME)
@Target({ TYPE, METHOD })
public @interface GeneratedByATrustedSource {

}

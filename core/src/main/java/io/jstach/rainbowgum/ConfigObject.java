package io.jstach.rainbowgum;

import static java.lang.annotation.RetentionPolicy.CLASS;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;

/**
 * Used to generate Rainbow Gum config objects
 */
@Retention(CLASS)
@Target({ ElementType.CONSTRUCTOR, ElementType.METHOD })
@Documented
public @interface ConfigObject {

	/**
	 * Name of builder.
	 * @return name of builder.
	 */
	String name();

	/**
	 * Property prefix.
	 * @return prefix
	 */
	String prefix();

}

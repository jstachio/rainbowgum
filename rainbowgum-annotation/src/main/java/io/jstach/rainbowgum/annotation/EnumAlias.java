package io.jstach.rainbowgum.annotation;

import static java.lang.annotation.ElementType.FIELD;
import static java.lang.annotation.RetentionPolicy.SOURCE;

import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;

/**
 * API documentation marker on an enum constant listing the other property values, case
 * insensitive, that select it besides its own name, for example <code>true</code> or
 * <code>false</code>.
 *
 * @apiNote Rainbow Gum's annotation processor records these aliases in generated resource
 * files for its own tests. Those files are not public API: their names, location, and
 * format may change in any release and must not be relied on.
 */
@Documented
@Retention(SOURCE)
@Target(FIELD)
public @interface EnumAlias {

	/**
	 * The aliases.
	 * @return aliases.
	 */
	String[] value();

}

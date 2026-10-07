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
 * <p>
 * Property enums follow the same conventions so that users can guess values without
 * looking them up:
 * <ul>
 * <li><code>true</code> turns the thing on or shows it, usually in its default form; when
 * the default is off, <code>true</code> selects the usual on choice instead (TTLL key
 * values: <code>logfmt</code>).</li>
 * <li><code>false</code> turns the thing off or hides it, selecting the constant named
 * <code>OFF</code> or <code>NONE</code>.</li>
 * <li><code>default</code> restores the default without naming it, so a later property
 * source can undo an earlier one; it differs from <code>true</code> only when the default
 * is off. When a constant is itself named <code>DEFAULT</code>, it is not aliased.</li>
 * <li>A constant named <code>OFF</code> is for a switch or mode that is turned off
 * (color, strict checking, debug output); a constant named <code>NONE</code> is for a
 * part or piece of content that is left out (no timestamp, no key values, no caller
 * info).</li>
 * <li>An alias never repeats a constant's own name; names already match case
 * insensitively.</li>
 * </ul>
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

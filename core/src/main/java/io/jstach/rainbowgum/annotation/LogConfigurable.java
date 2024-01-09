package io.jstach.rainbowgum.annotation;

import static java.lang.annotation.RetentionPolicy.CLASS;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;

/**
 * Used to generate Rainbow Gum config builder objects that once built will call the
 * method annotated with the properties from the generated builder on build.
 */
@Retention(CLASS)
@Target({ ElementType.CONSTRUCTOR, ElementType.METHOD })
@Documented
public @interface LogConfigurable {

	/**
	 * Name of builder.
	 * @return name of builder by default if not set Builder will be suffixed to
	 * targetType.
	 */
	String name() default "";

	/**
	 * Property prefix.
	 * @return prefix
	 */
	String prefix();

	/**
	 * Use as parameter to prefix property names.
	 */
	@Retention(CLASS)
	@Target({ ElementType.PARAMETER })
	@Documented
	public @interface PrefixParameter {

		/**
		 * Use as parameter to prefix property names.
		 * @return by default will use the parameter name.
		 */
		String value() default "";

	}

	/**
	 * Use to set static defaults to parameters.
	 */
	@Retention(CLASS)
	@Target({ ElementType.PARAMETER })
	@Documented
	public @interface DefaultParameter {

		/**
		 * Use as parameter to prefix property names.
		 * @return by default will use the static field
		 * <code>DEFAULT_parameterName</code>.
		 */
		String value() default "";

	}

	/**
	 * Use to set static defaults to parameters.
	 */
	@Retention(CLASS)
	@Target({ ElementType.PARAMETER })
	@Documented
	public @interface ConvertParameter {

		/**
		 * Static method on target type to call to convert the parameter. The method must
		 * be return a type and have a single argument.
		 * @return static method name.
		 */
		String value();

	}

}

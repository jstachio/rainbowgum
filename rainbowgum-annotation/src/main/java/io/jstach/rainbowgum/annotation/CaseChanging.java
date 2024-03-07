package io.jstach.rainbowgum.annotation;

import static java.lang.annotation.ElementType.TYPE;
import static java.lang.annotation.RetentionPolicy.SOURCE;

import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;

/**
 * API documentation marker that the enum or sealed type may add new case values on
 * feature release and thus external API consumers desiring backward compatibility should
 * have a <code>default</code> case.
 */
@Documented
@Retention(SOURCE)
@Target(TYPE)
public @interface CaseChanging {

}

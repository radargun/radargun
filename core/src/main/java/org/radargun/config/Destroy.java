package org.radargun.config;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Method annotated with this annotation should perform object cleanup.
 * The destroy methods in superclasses are called after methods in subclasses.
 * If a class has multiple destroy methods, the order in which these are called is not defined.
 *
 * @author Radim Vansa &lt;rvansa@redhat.com&gt;
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface Destroy {
}

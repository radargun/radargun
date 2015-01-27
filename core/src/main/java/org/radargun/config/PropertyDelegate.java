package org.radargun.config;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Delegates property as set up to different instance.
 *
 * @author Radim Vansa &lt;rvansa@redhat.com&gt;
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.FIELD)
public @interface PropertyDelegate {
   /**
    * Properties prefixed with this prefix will be delegated to the target object,
    * the prefix will be stripped out.
    */
   String prefix() default "";
}

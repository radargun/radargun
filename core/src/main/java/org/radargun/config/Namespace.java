package org.radargun.config;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Defines the target namespace for this class when generating elements in XML schema.
 *
 * @author Radim Vansa &lt;rvansa@redhat.com&gt;
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE) // TODO: allow package level annotation?
public @interface Namespace {
   static final String NO_DEPRECATED_NAME = "__no_deprecated_name__";

   String name();
   String deprecatedName() default NO_DEPRECATED_NAME;
}

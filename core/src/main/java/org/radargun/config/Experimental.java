package org.radargun.config;

import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;

import static java.lang.annotation.ElementType.*;
import static java.lang.annotation.RetentionPolicy.CLASS;

/**
 * An experimental user-facing API. Elements annotated with this annotation
 * are experimental and may get removed from the distribution at any time.
 *
 */
@Retention(CLASS)
@Target({PACKAGE, TYPE, ANNOTATION_TYPE, METHOD, CONSTRUCTOR, FIELD})
@Documented
public @interface Experimental {
   String value() default "";
}

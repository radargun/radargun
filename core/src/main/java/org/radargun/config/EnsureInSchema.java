package org.radargun.config;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Make sure that class annotated by this element ends up in schema.
 * This is necessary when the class is used as property delegate in another
 * namespace (but the element belongs to its own namespace)..
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface EnsureInSchema {
}

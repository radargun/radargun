package org.radargun.config;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Defines the target namespace for this class when generating elements in XML schema.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE) // TODO: allow package level annotation?
public @interface Namespace {
   String value();
}

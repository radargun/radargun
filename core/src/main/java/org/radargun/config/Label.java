package org.radargun.config;

/**
 * Label configuration for stage
 */
public @interface Label {
   String prefix() default "";

   String suffix() default "";

   String separator() default ".";
}

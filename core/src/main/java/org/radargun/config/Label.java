package org.radargun.config;

/**
 * Label configuration for stage
 *
 * @author Radim Vansa &lt;rvansa@redhat.com&gt;
 */
public @interface Label {
   String prefix() default "";

   String suffix() default "";

   String separator() default ".";
}

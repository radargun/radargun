package org.radargun.config;

import java.lang.annotation.*;

/**
 * This setter should be exposed as XML attribute (stage property)
 *
 * @author Radim Vansa &lt;rvansa@redhat.com&gt;
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.FIELD)
public @interface Property {
   static final String FIELD_NAME = "__use_field_name_as_property_name__";
   static final String NO_DEPRECATED_NAME = "__no_deprecated_name__";
   String name() default FIELD_NAME;
   String deprecatedName() default NO_DEPRECATED_NAME;
   Class<? extends Converter<?>> converter() default DefaultConverter.class;
   boolean optional() default true;
   String doc();
   /* This property cannot be configured from XML but will be printed out */
   boolean readonly() default false;
}



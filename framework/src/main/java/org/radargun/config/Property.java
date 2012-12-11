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
   final static String FIELD_NAME = "__use_field_name_as_property_name__";
   String name() default FIELD_NAME;
   Class<? extends Converter<?>> converter() default Converter.DefaultConverter.class;
   boolean optional() default true;
   String doc();
}



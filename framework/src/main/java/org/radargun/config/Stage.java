package org.radargun.config;

import java.lang.annotation.*;

/**
 * This is a benchmark stage
 *
 * @author Radim Vansa &lt;rvansa@redhat.com&gt;
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface Stage {
   static final String CLASS_NAME_WITHOUT_STAGE = "__class_name_without_Stage__";
   static final String NO_DEPRECATED_NAME = "__no_deprecated_name__";
   String name() default CLASS_NAME_WITHOUT_STAGE;
   String deprecatedName() default NO_DEPRECATED_NAME;
   String doc();
}

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
   String CLASS_NAME_WITHOUT_STAGE = "__class_name_without_Stage__";
   String name() default CLASS_NAME_WITHOUT_STAGE;
   String doc();
}

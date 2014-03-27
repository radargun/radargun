package org.radargun.traits;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Fields in Stages annotated by this annotation are injected with actual implementation provided by Service,
 * or set to null if there is the Trait is not provided.
 *
 * @author Radim Vansa &lt;rvansa@redhat.com&gt;
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.FIELD)
public @interface InjectTrait {
   public enum Dependency {
      OPTIONAL,  // not filled, null
      MANDATORY, // the stage should fail with error
      SKIP       // the stage should be skipped
   }

   Dependency dependency() default Dependency.OPTIONAL;
}

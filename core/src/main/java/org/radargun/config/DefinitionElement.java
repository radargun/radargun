package org.radargun.config;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Class annotated with this can be instantiated from a sub-element in XML configuration.
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface DefinitionElement {
   String name();

   String doc();

   ResolveType resolveType() default ResolveType.PASS_BY_MAP;

   enum ResolveType {
      /**
       * The object should have properties resolved by transformation
       * of the definition into map of property-definition.
       */
      PASS_BY_MAP,
      /**
       * This class has only single property with empty name, and the definition
       * should be passed directly to that property.
       */
      PASS_BY_DEFINITION
   }
}

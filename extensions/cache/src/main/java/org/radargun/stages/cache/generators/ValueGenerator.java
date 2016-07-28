package org.radargun.stages.cache.generators;

import java.util.Random;

import org.radargun.utils.ReflexiveConverters;

/**
 * Factory class which generates the values used for stress testing
 */
public interface ValueGenerator {
   String VALUE_GENERATOR = "VALUE_GENERATOR";

   Object generateValue(Object key, int size, Random random);

   int sizeOf(Object value);

   boolean checkValue(Object value, Object key, int expectedSize);

   public static class ComplexConverter extends ReflexiveConverters.ObjectConverter {
      public ComplexConverter() {
         super(ValueGenerator.class);
      }
   }
}

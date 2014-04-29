package org.radargun.stages.cache.generators;

import java.util.Random;

/**
 * Factory class which generates the values used for stress testing
 *
 * @author Radim Vansa &lt;rvansa@redhat.com&gt;
 */
public interface ValueGenerator {

   String VALUE_GENERATOR = "VALUE_GENERATOR";

   /**
    * @param param   Generic argument for the generator
    * @param classLoader Class loader that should be used if the generator will load some classes via reflection.
    *
    */
   void init(String param, ClassLoader classLoader);

   Object generateValue(Object key, int size, Random random);

   int sizeOf(Object value);

   boolean checkValue(Object value, int expectedSize);
}

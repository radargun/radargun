package org.radargun.stages.cache.generators;

import java.util.Random;

/**
 * Factory class which generates the values used for stress testing
 *
 * @author Radim Vansa &lt;rvansa@redhat.com&gt;
 */
public interface ValueGenerator {
   String VALUE_GENERATOR = "VALUE_GENERATOR";

   Object generateValue(Object key, int size, Random random);

   int sizeOf(Object value);

   boolean checkValue(Object value, Object key, int expectedSize);
}

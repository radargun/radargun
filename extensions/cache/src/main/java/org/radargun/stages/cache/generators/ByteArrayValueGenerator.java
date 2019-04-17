package org.radargun.stages.cache.generators;

import java.util.Random;

import org.radargun.config.DefinitionElement;

import static org.radargun.stages.cache.generators.ByteArrayGenerator.generateArray;

/**
 * @author Radim Vansa &lt;rvansa@redhat.com&gt;
 */
@DefinitionElement(name = "byte-array", doc = "Generates random byte arrays.")
public class ByteArrayValueGenerator implements ValueGenerator {
   @Override
   public Object generateValue(Object key, int size, Random random) {
      return generateArray(size, random);
   }

   @Override
   public int sizeOf(Object value) {
      return ((byte[]) value).length;
   }

   @Override
   public boolean checkValue(Object value, Object key, int expectedSize) {
      return value instanceof byte[] && (expectedSize <= 0 || ((byte[]) value).length == expectedSize);
   }
}

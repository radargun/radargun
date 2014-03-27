package org.radargun.stages.cache.generators;

import java.util.Random;

/**
 * @author Radim Vansa &lt;rvansa@redhat.com&gt;
 */
public class ByteArrayValueGenerator implements ValueGenerator {
   @Override
   public void init(String param, ClassLoader classLoader) {
   }

   @Override
   public Object generateValue(Object key, int size, Random random) {
      return generateArray(size, random);
   }

   @Override
   public int sizeOf(Object value) {
      return ((byte[]) value).length;
   }

   @Override
   public boolean checkValue(Object value, int expectedSize) {
      return value instanceof byte[] && (expectedSize <= 0 || ((byte[]) value).length == expectedSize);
   }

   public static byte[] generateArray(int size, Random random) {
      byte[] array = new byte[size];
      random.nextBytes(array);
      return array;
   }
}

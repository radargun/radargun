package org.radargun.util;

import org.radargun.stages.cache.generators.KeyGenerator;

/**
 * General test tools.
 *
 * @author Matej Cimbora
 */
public final class CacheTestUtils {

   private CacheTestUtils() {
   }

   public static class TestException extends RuntimeException {

      public TestException() {
      }
   }

   public static class SimpleStringKeyGenerator implements KeyGenerator {

      @Override
      public Object generateKey(long keyIndex) {
         return String.valueOf(keyIndex);
      }
   }

}

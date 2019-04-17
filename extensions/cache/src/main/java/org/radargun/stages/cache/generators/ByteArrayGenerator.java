package org.radargun.stages.cache.generators;

import java.util.Random;

public class ByteArrayGenerator {

   private ByteArrayGenerator() {

   }

   public static byte[] generateArray(int size, Random random) {
      byte[] array = new byte[size];
      random.nextBytes(array);
      return array;
   }
}

package org.radargun.utils;

import java.util.Random;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Helper to create random strings of A-Z characters.
 *
 * @author Radim Vansa &lt;rvansa@redhat.com&gt;
 */
public final class RandomHelper {

   private RandomHelper() {}

   public static String randomString(int minLength, int maxLength) {
      Random random = ThreadLocalRandom.current();
      return randomString(minLength, maxLength, random);
   }

   public static String randomString(int minLength, int maxLength, Random random) {
      return randomStringBuilder(minLength, maxLength, random).toString();
   }

   public static StringBuilder randomStringBuilder(int minLength, int maxLength, Random random) {
      int length;
      if (minLength > maxLength) throw new IllegalArgumentException();
      if (minLength == maxLength) {
         length = minLength;
      } else {
         length = minLength + random.nextInt(maxLength - minLength);
      }
      StringBuilder sb = new StringBuilder(length);
      for (int i = length; i > 0; --i) {
         sb.append((char) (random.nextInt('Z' - 'A') + 'A'));
      }
      return sb;
   }
}

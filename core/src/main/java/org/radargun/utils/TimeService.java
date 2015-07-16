package org.radargun.utils;

/**
 * Global service providing time operations.
 *
 * @author Matej Cimbora
 */
public final class TimeService {

   private TimeService() {
   }

   public static long currentTimeMillis() {
      return System.currentTimeMillis();
   }

   public static long nanoTime() {
      return System.nanoTime();
   }
}

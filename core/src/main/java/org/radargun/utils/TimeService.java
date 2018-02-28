package org.radargun.utils;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.LockSupport;
import java.util.function.BooleanSupplier;

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

   public static void waitFor(BooleanSupplier condition) {
      TimeService.waitFor(condition, 60, TimeUnit.SECONDS, 1);
   }

   public static void waitFor(BooleanSupplier condition, long timeout, TimeUnit unit, int numberOfSuccessfulChecks) {
      final int maxNumberOfBackofs = 100;
      long endTime = TimeUnit.MILLISECONDS.convert(timeout, unit) + System.currentTimeMillis();
      long backoffCounter = 0;
      int successfulChecks = 0;
      while (System.currentTimeMillis() - endTime < 0) {
         if (condition.getAsBoolean()) {
            if (++successfulChecks >= numberOfSuccessfulChecks) {
               break;
            }
         } else {
            successfulChecks = 0;
         }
         backoffCounter = backoffCounter + 1 > maxNumberOfBackofs ? maxNumberOfBackofs : backoffCounter + 1;
         LockSupport.parkNanos(++backoffCounter * 100_000_000);
      }
      if (!condition.getAsBoolean()) {
         throw new RuntimeException("Timed out waiting for a condition");
      }
   }
}

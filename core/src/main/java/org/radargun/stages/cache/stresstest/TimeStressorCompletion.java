package org.radargun.stages.cache.stresstest;

import java.util.concurrent.TimeUnit;

import org.radargun.utils.Utils;

/**
 * Time-based limitation of the stress test.
 *
 * @author Mircea Marcus
 */
public class TimeStressorCompletion extends Completion {

   private volatile long startTime = -1;
   private volatile long lastPrint = -1;
   private final long duration;
   private final long logFrequency = TimeUnit.SECONDS.toNanos(20);

   /**
    * @param duration Duration of the test in nanoseconds.
    */
   public TimeStressorCompletion(long duration) {
      this.duration = TimeUnit.MILLISECONDS.toNanos(duration);
   }

   @Override
   public boolean moreToRun() {
      // Synchronize the start until someone is ready
      // we don't care about the race condition here
      if (startTime == -1) {
         startTime = System.nanoTime();
      }
      return System.nanoTime() <= startTime + duration;
   }

   @Override
   public void logProgress(int executedOps) {
      long now = System.nanoTime();
      //make sure this info is not printed more frequently than 20 secs
      if (lastPrint < 0 || (now - lastPrint) < logFrequency) {
         return;
      }
      synchronized (this) {
         if (now - lastPrint < logFrequency) return;
         lastPrint = now;
         //make sure negative durations are not printed
         long remaining = Math.max(0, (startTime + duration) - now);
         log.info(String.format(PROGRESS_STRING, executedOps, Utils.getNanosDurationString(now - startTime),
               Utils.getNanosDurationString(remaining), Utils.getNanosDurationString(duration)));
      }
   }
}

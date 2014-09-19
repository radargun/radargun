package org.radargun.stages.test;

import java.util.Random;
import java.util.concurrent.TimeUnit;

import org.radargun.logging.Log;
import org.radargun.logging.LogFactory;

/**
 * Limits the duration of stress test.
 *
 * @author Radim Vansa &lt;rvansa@redhat.com&gt;
 */
public abstract class Completion {
   static final String PROGRESS_STRING = "Number of operations executed by this thread: %d. Elapsed time: %s. Remaining: %s. Total: %s.";
   protected final static Log log = LogFactory.getLog(Completion.class);
   protected final long requestPeriod;
   // prevents non-intended synchronization
   protected final ThreadLocal<Long> privateRampUp = new ThreadLocal<>();
   protected volatile long startTime = -1;

   public Completion(long requestPeriod) {
      this.requestPeriod = TimeUnit.MILLISECONDS.toNanos(requestPeriod);
   }

   /**
    * @return True if the stress test execution should continue
    * @param opNumber
    */
   public abstract boolean moreToRun(int opNumber);

   /**
    * Optionally writes progress message to the log.
    * @param executedOps Number of operations executed by this stressor thread.
    */
   public abstract void logProgress(int executedOps);

   protected void waitForNextRequest(int opNumber, long now) {
      if (startTime < 0) {
         startTime = System.nanoTime();
      }
      if (requestPeriod > 0) {
         Long rampUp = privateRampUp.get();
         if (rampUp == null) {
            rampUp = new Random(Thread.currentThread().getId() ^ System.nanoTime()).nextLong() % requestPeriod;
            privateRampUp.set(rampUp);
         }
         long waitTime = TimeUnit.NANOSECONDS.toMillis(startTime + rampUp + requestPeriod * opNumber - now);
         // for times < 1ms, do not wait
         if (waitTime > 0) {
            try {
               Thread.sleep(waitTime);
            } catch (InterruptedException e) {
               Thread.currentThread().interrupt();
            }
         }
      }
   }
}

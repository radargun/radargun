package org.radargun.stages.test;

import java.util.Random;
import java.util.concurrent.TimeUnit;

import org.radargun.logging.Log;
import org.radargun.logging.LogFactory;

/**
 * @author Radim Vansa &lt;rvansa@redhat.com&gt;
 */
public abstract class AbstractCompletion implements Completion {
   static final String PROGRESS_STRING = "Number of operations executed by this thread: %d. Elapsed time: %s. Remaining: %s. Total: %s.";
   protected final static Log log = LogFactory.getLog(AbstractCompletion.class);
   protected final long requestPeriod;
   // prevents non-intended synchronization
   protected final ThreadLocal<Long> privateRampUp = new ThreadLocal<>();
   protected volatile long startTime = -1;

   public AbstractCompletion(long requestPeriod) {
      this.requestPeriod = TimeUnit.MILLISECONDS.toNanos(requestPeriod);
   }

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

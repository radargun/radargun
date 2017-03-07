package org.radargun.stages.test;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

import org.radargun.utils.TimeService;
import org.radargun.utils.Utils;

/**
 * Operation count-based limitation of the stress test.
 *
 * @author Alan Field &lt;afield@redhat.com&gt;
 */
public class CountStressorCompletion extends AbstractCompletion {

   protected static final String PROGRESS_STRING = "Number of operations executed by this thread: %d. Elapsed time: %s. Operations Remaining: %s. Total Operations: %s.";
   private volatile long lastPrint = -1;
   private final long operationsPerNode;
   private final long logFrequency = TimeUnit.SECONDS.toNanos(20);
   private final AtomicLong operationCount = new AtomicLong(0);

   public CountStressorCompletion(long opCount) {
      this.operationsPerNode = opCount;
   }

   @Override
   public boolean moreToRun() {
      boolean moreToRun = operationCount.incrementAndGet() <= operationsPerNode;
      if (!moreToRun) {
         runCompletionHandler();
      }
      return moreToRun;
   }

   @Override
   public void logProgress(int executedOps) {
      long now = TimeService.nanoTime();
      //make sure this info is not printed more frequently than 20 secs
      if (lastPrint < 0 || (now - lastPrint) < logFrequency) {
         return;
      }
      synchronized (this) {
         if (now - lastPrint < logFrequency)
            return;
         lastPrint = now;
         log.infof(PROGRESS_STRING, executedOps, Utils.getNanosDurationString(now - startTime),
               operationCount.toString(), operationsPerNode);
      }
   }
}

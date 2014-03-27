package org.radargun.stages.cache.stresstest;

import java.util.concurrent.atomic.AtomicLong;

import org.radargun.utils.Utils;

/**
* @author Radim Vansa &lt;rvansa@redhat.com&gt;
*/
public class OperationCountCompletion extends Completion {

   private final AtomicLong requestsLeft;
   private final long numRequests;
   private final long logOps;
   private volatile long startTime = -1;

   public OperationCountCompletion(long numRequests, long logPeriod) {
      this.requestsLeft = new AtomicLong(numRequests);
      this.numRequests = numRequests;
      this.logOps = logPeriod;
   }

   @Override
   public boolean moreToRun() {
      if (startTime < 0) {
         startTime = System.nanoTime();
      }
      return requestsLeft.getAndDecrement() > 0;
   }

   public void logProgress(int executedOps) {
      long totalExecuted = numRequests - requestsLeft.get();
      if ((totalExecuted + 1) % logOps != 0) {
         return;
      }
      long elapsedNanos = System.nanoTime() - startTime;
      long estimatedTotal = elapsedNanos * numRequests / totalExecuted;
      long estimatedRemaining = estimatedTotal - elapsedNanos;
      log.info(String.format(PROGRESS_STRING, executedOps, Utils.getNanosDurationString(elapsedNanos), Utils.getNanosDurationString(estimatedRemaining) + " (estimated)", Utils.getNanosDurationString(estimatedTotal) + " (estimated)"));
   }
}

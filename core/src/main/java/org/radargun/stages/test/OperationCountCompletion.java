package org.radargun.stages.test;

import java.util.concurrent.atomic.AtomicLong;

import org.radargun.utils.TimeService;
import org.radargun.utils.Utils;

/**
 * Completion limited by absolute number of executed operations.
 * All stressors share the total number of requested operations.
 *
 * @author Radim Vansa &lt;rvansa@redhat.com&gt;
 */
public class OperationCountCompletion extends AbstractCompletion {

   private final AtomicLong requestsLeft;
   private final long numRequests;
   private final long logOps;

   public OperationCountCompletion(long numRequests, long requestPeriod, long logPeriod) {
      super(requestPeriod);
      this.requestsLeft = new AtomicLong(numRequests);
      this.numRequests = numRequests;
      this.logOps = logPeriod;
   }

   @Override
   public boolean moreToRun(int opNumber) {
      waitForNextRequest(opNumber, TimeService.nanoTime());
      return requestsLeft.getAndDecrement() > 0;
   }

   @Override
   public void logProgress(int executedOps) {
      long totalExecuted = numRequests - requestsLeft.get();
      if ((totalExecuted + 1) % logOps != 0) {
         return;
      }
      long elapsedNanos = TimeService.nanoTime() - startTime;
      long estimatedTotal = elapsedNanos * numRequests / totalExecuted;
      long estimatedRemaining = estimatedTotal - elapsedNanos;
      log.infof(PROGRESS_STRING, executedOps, Utils.getNanosDurationString(elapsedNanos), Utils.getNanosDurationString(estimatedRemaining) + " (estimated)", Utils.getNanosDurationString(estimatedTotal) + " (estimated)");
   }
}

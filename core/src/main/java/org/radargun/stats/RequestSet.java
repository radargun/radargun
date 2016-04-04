package org.radargun.stats;

import org.radargun.Operation;

/**
 * @author Radim Vansa &lt;rvansa@redhat.com&gt;
 */
public final class RequestSet {
   private final Statistics statistics;
   private long begin = Long.MAX_VALUE;
   private long end = Long.MIN_VALUE;
   private long sumDurations = 0;
   private boolean successful = true;

   public RequestSet(Statistics statistics) {
      this.statistics = statistics;
   }

   public void add(Request request) {
      if (!request.isFinished()) {
         throw new IllegalArgumentException();
      }
      begin = Math.min(begin, request.getRequestStartTime());
      end = Math.max(end, request.getResponseCompleteTime());
      sumDurations += request.duration();
   }

   public void finished(boolean successful, Operation operation) {
      statistics.record(this, operation);
      this.successful = successful;
   }

   public void succeeded(Operation operation) {
      statistics.record(this, operation);
   }

   public void failed(Operation operation) {
      successful = false;
      statistics.record(this, operation);
   }

   public void discard() {
      statistics.discard(this);
   }

   public long sumDurations() {
      return sumDurations;
   }

   public long getBegin() {
      return begin;
   }

   public long durationSpan() {
      return end - begin;
   }

   public boolean isSuccessful() {
      return successful;
   }
}

package org.radargun.stressors;

/**
 * // TODO: Document this
 *
 * @author Radim Vansa &lt;rvansa@redhat.com&gt;
 * @since 1/28/13
 */
public class SynchronizedStatistics extends Statistics {

   protected boolean snapshot = false;

   public SynchronizedStatistics(boolean nodeUp) {
      super(nodeUp);
   }

   public SynchronizedStatistics() {
   }

   @Override
   protected SynchronizedStatistics create() {
      return new SynchronizedStatistics();
   }

   public boolean isSnapshot() {
      return snapshot;
   }

   protected void ensureSnapshot() {
      if (!snapshot) {
         throw new RuntimeException("this operation can be performed only on snapshot");
      }
   }

   protected void ensureNotSnapshot() {
      if (snapshot) {
         throw new RuntimeException("this operation cannot be performed on snapshot");
      }
   }

   public synchronized SynchronizedStatistics snapshot(boolean reset, long time) {
      ensureNotSnapshot();
      SynchronizedStatistics result = create();

      fillCopy(result);

      result.intervalEndTime = time;
      result.snapshot = true;
      result.nodeUp = nodeUp;
      if (reset) {
         reset(time);
      }
      return result;
   }


   public synchronized void registerRequest(long responseTime, Operation operation, boolean isNull) {
      ensureNotSnapshot();
      super.registerRequest(responseTime, 0, operation, isNull);
   }

   public synchronized void registerError(long responseTime, Operation operation) {
      ensureNotSnapshot();
      super.registerError(responseTime, 0, operation);
   }

   public synchronized void reset(long time) {
      ensureNotSnapshot();
      super.reset(time);
   }

   public synchronized SynchronizedStatistics copy() {
      SynchronizedStatistics copy = (SynchronizedStatistics) super.copy();
      copy.snapshot = snapshot;
      return copy;
   }

   public synchronized void merge(Statistics otherStats) {
      ensureSnapshot();
      if (otherStats instanceof SynchronizedStatistics) {
         ((SynchronizedStatistics) otherStats).ensureSnapshot();
      }
      super.merge(otherStats);
   }

   public synchronized long getNumberOfRequests() {
      return super.getNumberOfRequests();
   }

   public synchronized double getAvgResponseTime() {
      return super.getAvgResponseTime();
   }

   public synchronized long getDuration() {
      return super.getDuration();
   }

   public synchronized long getIntervalBeginTime() {
      return super.getIntervalBeginTime();
   }

   public synchronized long getIntervalEndTime() {
      return super.getIntervalEndTime();
   }

   public synchronized double getThroughput() {
      return super.getThroughput();
   }
}

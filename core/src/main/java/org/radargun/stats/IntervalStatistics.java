package org.radargun.stats;

/**
 * Base class holding just the begin-end timestamps.
 *
 * @author Radim Vansa &lt;rvansa@redhat.com&gt;
 */
public abstract class IntervalStatistics implements Statistics {
   private long beginTime = Long.MAX_VALUE;
   private long endTime = Long.MIN_VALUE;

   @Override
   public void begin() {
      beginTime = System.currentTimeMillis();
   }

   @Override
   public void end() {
      endTime = System.currentTimeMillis();
   }

   @Override
   public long getBegin() {
      return beginTime;
   }

   @Override
   public long getEnd() {
      return endTime;
   }

   public void setBegin(long beginTime) {
      this.beginTime = beginTime;
   }

   public void setEnd(long endTime) {
      this.endTime = endTime;
   }

   @Override
   public void merge(Statistics otherStats) {
      beginTime = Math.min(otherStats.getBegin(), beginTime);
      endTime = Math.max(otherStats.getEnd(), endTime);
   }

   @Override
   public String toString() {
      StringBuilder sb = new StringBuilder();
      sb.append(getClass().getSimpleName()).append(" {beginTime=");
      if (beginTime == Long.MAX_VALUE) sb.append("<none>");
      else sb.append(beginTime);
      sb.append(", endTime=");
      if (endTime == Long.MIN_VALUE) sb.append("<none>");
      else sb.append(endTime);
      return sb.append("}").toString();
   }
}

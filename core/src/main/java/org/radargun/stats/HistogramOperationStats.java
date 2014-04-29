package org.radargun.stats;

import java.util.Arrays;

import org.radargun.stats.representation.Histogram;

/**
 * Keeps several buckets for response time ranges and stores number of requests falling into this range.
 * Does not differentiate between successful and error requests.
 *
* @author Radim Vansa &lt;rvansa@redhat.com&gt;
*/
public final class HistogramOperationStats implements OperationStats {
   /* right non-inclusive borders */
   private long[] ranges;
   private long[] counts;
   private long min = Long.MAX_VALUE;
   private long max = Long.MIN_VALUE;

   public HistogramOperationStats(Histogram histogram) {
      this.ranges = histogram.ranges;
      counts = new long[ranges.length + 1];
   }

   @Override
   public void registerRequest(long responseTime) {
      int pos = Arrays.binarySearch(ranges, responseTime);
      if (pos >= 0) {
         counts[pos]++;
      } else {
         counts[-pos - 1]++;
      }
      min = Math.min(min, responseTime);
      max = Math.max(max, responseTime);
   }

   @Override
   public void registerError(long responseTime) {
      registerRequest(responseTime);
   }

   @Override
   public HistogramOperationStats copy() {
      HistogramOperationStats stats = new HistogramOperationStats(new Histogram(ranges, null, min, max));
      stats.counts = Arrays.copyOf(counts, counts.length);
      stats.min = min;
      stats.max = max;
      return stats;
   }

   @Override
   public void merge(OperationStats o) {
      if (!(o instanceof HistogramOperationStats)) throw new IllegalArgumentException();
      HistogramOperationStats other = (HistogramOperationStats) o;
      if (!Arrays.equals(ranges, other.ranges)) throw new IllegalArgumentException();
      for (int i = 0; i < counts.length; ++i) {
         counts[i] += other.counts[i];
      }
      min = Math.min(min, other.min);
      max = Math.max(max, other.max);
   }

   @Override
   public <T> T getRepresentation(Class<T> clazz) {
      if (clazz == Histogram.class) {
         return (T) new Histogram(ranges, null, min, max);
      }
      return null;
   }

   @Override
   public boolean isEmpty() {
      if (counts == null) return true;
      for (long count : counts) {
         if (count != 0) return false;
      }
      return true;
   }

   @Override
   public String toString() {
      if (max == Long.MIN_VALUE) {
         return "";
      }
      StringBuilder sb = new StringBuilder();
      sb.append(Math.min(min, ranges[0])).append(':');
      for (int i = 0; i < ranges.length; ++i) {
         sb.append(ranges[i]).append(':');
      }
      sb.append(Math.max(ranges[ranges.length - 1], max)).append('=');
      for (int i = 0; i < counts.length - 1; ++i) {
         sb.append(counts[i]).append(':');
      }
      sb.append(counts[counts.length - 1]);
      return sb.toString();
   }
}

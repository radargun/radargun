package org.radargun.stats.representation;

import org.radargun.utils.Utils;

/**
 * Representation holding buckets (time range) with number of results belonging to this bucket.
 *
 * @author Radim Vansa &lt;rvansa@redhat.com&gt;
 */
public final class Histogram {
   public final long[] ranges;
   public final long[] counts;

   /**
    * ranges[0] is minimum value, ranges[ranges.length -1] is maximum value.
    * counts[i] has number of such values X that ranges[i] <= X < ranges[i + 1] (with exception: X <= ranges[ranges.length - 1])
    * ranges.length == counts.length + 1
    *
    * @param ranges
    * @param counts
    */
   public Histogram(long[] ranges, long[] counts) {
      this.ranges = ranges;
      this.counts = counts;
   }

   /**
    * @param args
    * @return Number of buckets the histogram should contain (counts.length)
    */
   public static int getBuckets(Object[] args) {
      int buckets = Utils.getArg(args, 0, Integer.class);
      if (buckets <= 0) throw new IllegalArgumentException(String.valueOf(buckets));
      return buckets;
   }

   /**
    * @param args
    * @return In order to truncate long tail, display only lower <= percentile values
    */
   public static double getPercentile(Object[] args) {
      double percentile = Utils.getArg(args, 1, Double.class);
      if (percentile <= 0 || percentile > 100) throw new IllegalArgumentException(String.valueOf(percentile));
      return percentile;
   }
}

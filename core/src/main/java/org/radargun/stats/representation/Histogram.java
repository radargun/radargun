package org.radargun.stats.representation;

import java.util.Arrays;

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
      if (args == null || args.length < 1 || !(args[0] instanceof Integer)) throw new IllegalArgumentException(Arrays.toString(args));
      int buckets = (Integer) args[0];
      if (buckets <= 0) throw new IllegalArgumentException(String.valueOf(buckets));
      return buckets;
   }

   /**
    * @param args
    * @return In order to truncate long tail, display only lower <= percentile values
    */
   public static double getPercentile(Object[] args) {
      if (args == null || args.length < 2 || !(args[1] instanceof Double)) throw new IllegalArgumentException(Arrays.toString(args));
      double percentile = (Double) args[1];
      if (percentile <= 0 || percentile > 100) throw new IllegalArgumentException(String.valueOf(percentile));
      return percentile;
   }
}

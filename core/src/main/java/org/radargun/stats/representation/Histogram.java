package org.radargun.stats.representation;

/**
 * Representation holding buckets (time range) with number of results belonging to this bucket.
 *
 * @author Radim Vansa &lt;rvansa@redhat.com&gt;
 */
public final class Histogram {
   /* right non-inclusive borders */
   public final long[] ranges;
   public final long[] counts;
   public final long min;
   public final long max;
   public static final String OPERATIONS_HISTOGRAMS = "__OPERATIONS_HISTOGRAMS__";

   public Histogram(long[] ranges, long[] counts, long min, long max) {
      this.ranges = ranges;
      this.counts = counts;
      this.min = min;
      this.max = max;
   }
}

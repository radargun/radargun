package org.radargun.stats.representation;

import java.util.concurrent.TimeUnit;

/**
 * Min, max, and mean number of bytes per second.
 *
 * @author Alan Field &lt;afield@redhat.com&gt;
 */
public class DataThroughput {
   /**
    * Min, max, and mean data throughput of the executed operations.
    */
   public final double minThroughput;
   public final double maxThroughput;
   public final double meanThroughput;

   public DataThroughput(double min, double max, double mean) {
      this.minThroughput = min;
      this.maxThroughput = max;
      this.meanThroughput = mean;
   }

   public static DataThroughput compute(long totalBytes, long minDurationNanos, long maxDurationNanos,
         double meanDurationNanos) {
      double minDurationSecs = (double) minDurationNanos / TimeUnit.SECONDS.toNanos(1);
      double maxDurationSecs = (double) maxDurationNanos / TimeUnit.SECONDS.toNanos(1);
      double meanDurationSecs = (double) meanDurationNanos / TimeUnit.SECONDS.toNanos(1);
      if (minDurationSecs == 0 || maxDurationSecs == 0 || meanDurationSecs == 0) {
         return null;
      }
      return new DataThroughput(totalBytes / maxDurationSecs, totalBytes / minDurationSecs, totalBytes
            / meanDurationSecs);
   }

}

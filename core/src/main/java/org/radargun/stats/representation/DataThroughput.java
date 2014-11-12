package org.radargun.stats.representation;

import java.util.concurrent.TimeUnit;

/**
 * Number of megabytes per second.
 *
 * @author Alan Field &lt;afield@redhat.com&gt;
 */
public class DataThroughput {
   /**
    * Actual data throughput of the executed operations.
    */
   public final double actual;

   public DataThroughput(double actual) {
      this.actual = actual;
   }

   public static DataThroughput compute(long totalBytes, long durationNanos) {
      double totalMB = totalBytes / (1024.0 * 1024.0);
      double durationSecs = (double) durationNanos / TimeUnit.SECONDS.toNanos(1);
      if (durationSecs == 0) {
         return null;
      }
      return new DataThroughput(totalMB / durationSecs);
   }

}

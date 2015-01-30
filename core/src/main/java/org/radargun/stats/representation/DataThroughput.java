package org.radargun.stats.representation;

import java.util.concurrent.TimeUnit;

/**
 * Min, max, mean, and std. dev number of bytes per second.
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
   public final double deviation;
   public final long[] responseTimes;
   public final long totalBytes;

   public DataThroughput(double min, double max, double mean, double deviation, long totalBytes, long[] responseTimes) {
      this.minThroughput = min;
      this.maxThroughput = max;
      this.meanThroughput = mean;
      this.deviation = deviation;
      this.responseTimes = responseTimes;
      this.totalBytes =totalBytes;
   }

   public static DataThroughput compute(long totalBytes, long[] responseTimes, int requestCount) {
      double minDurationSecs = Long.MAX_VALUE;
      double maxDurationSecs = Long.MIN_VALUE;
      double meanDurationSecs = 0;
      double[] throughputs = new double[requestCount];
      long[] requestTimes = new long[requestCount];

      if (requestCount == 0) {
         return new DataThroughput(0, 0, 0, 0, 0, new long[0]);
      } else {
         for (int i = 0; i < requestCount; ++i) {
            minDurationSecs = Math.min(minDurationSecs, responseTimes[i]);
            maxDurationSecs = Math.max(maxDurationSecs, responseTimes[i]);
            meanDurationSecs += responseTimes[i];
            throughputs[i] = totalBytes / toSecs(responseTimes[i]);
            requestTimes[i] = responseTimes[i];
         }
         minDurationSecs = toSecs(minDurationSecs);
         maxDurationSecs = toSecs(maxDurationSecs);
         meanDurationSecs = toSecs(meanDurationSecs / requestCount);
      }
      return new DataThroughput(totalBytes / maxDurationSecs, totalBytes / minDurationSecs, totalBytes
            / meanDurationSecs, getDeviation(throughputs), totalBytes, requestTimes);
   }

   private static double toSecs(double nanos) {
      return nanos / TimeUnit.SECONDS.toNanos(1);
   }

   private static double getDeviation(double[] throughputs) {
      double temp = 0;
      double meanThroughput = 0;
      if (throughputs.length < 2) {
         return 0;
      } else {
         for (int i = 0; i < throughputs.length; ++i) {
            meanThroughput += throughputs[i];
         }
         meanThroughput = meanThroughput / throughputs.length;
         for (int i = 0; i < throughputs.length; ++i) {
            temp += ((meanThroughput - throughputs[i]) * (meanThroughput - throughputs[i]));
         }
         return Math.sqrt(temp / (throughputs.length - 1));
      }
   }

}

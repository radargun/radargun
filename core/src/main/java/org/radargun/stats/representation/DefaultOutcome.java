package org.radargun.stats.representation;

import java.util.concurrent.TimeUnit;

/**
 * Representation holding of successful/unsuccessful requests, mean and max response time.
 *
 * @author Radim Vansa &lt;rvansa@redhat.com&gt;
 */
public class DefaultOutcome {
   public final long requests;
   public final long errors;
   /**
    * Mean response time, in nanoseconds.
    */
   public final double responseTimeMean;
   /**
    * Max response time, in nanoseconds.
    */
   public final long responseTimeMax;

   public DefaultOutcome(long requests, long errors, double responseTimeMean, long responseTimeMax) {
      this.requests = requests;
      this.errors = errors;
      this.responseTimeMean = responseTimeMean;
      this.responseTimeMax = responseTimeMax;
   }

   /**
    * Throughput cannot be obtained from the operation stats directly as it depends on the number of statistics
    * that have been merged, and actual throughput on the measuring period alltogether.

    * @param mergedCount Number of independent stressors executing the operations.
    * @param duration Benchmarking period, in nanoseconds.
    * @return
    */
   public Throughput toThroughput(int mergedCount, long duration) {
      return new Throughput(TimeUnit.SECONDS.toNanos(1) * mergedCount / responseTimeMean,
            TimeUnit.SECONDS.toNanos(1) * (double) requests / duration);
   }
}

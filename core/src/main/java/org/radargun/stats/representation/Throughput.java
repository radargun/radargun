package org.radargun.stats.representation;

import java.util.concurrent.TimeUnit;

import org.radargun.utils.Utils;

/**
 * Number of operations per second.
 *
 * @author Radim Vansa &lt;rvansa@redhat.com&gt;
 */
public class Throughput {
   /**
    * Inverse to the response time, multiplied by number of threads.
    */
   public final double theoretical;
   /**
    * Actual throughput of the executed operations. May be imprecise if the merged periods are not identical.
    */
   public final double actual;

   public Throughput(double theoretical, double actual) {
      this.theoretical = theoretical;
      this.actual = actual;
   }

   public static Throughput compute(long requests, double responseTimeMean, Object[] args) {
      int threads = Throughput.getThreads(args);
      long duration = Throughput.getDuration(args);
      if (duration == 0) return null;
      return new Throughput(TimeUnit.SECONDS.toNanos(1) * threads / responseTimeMean,
            TimeUnit.SECONDS.toNanos(1) * (double) requests / duration);
   }

   public static int getThreads(Object[] args) {
      int threads = Utils.getArg(args, 0, Integer.class);
      if (threads < 0) throw new IllegalArgumentException(String.valueOf(threads));
      return threads;
   }

   public static long getDuration(Object[] args) {
      long duration = Utils.getArg(args, 1, Long.class);
      if (duration < 0) throw new IllegalArgumentException(String.valueOf(duration));
      return duration;
   }
}

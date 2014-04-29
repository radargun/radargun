package org.radargun.stats.representation;

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
}

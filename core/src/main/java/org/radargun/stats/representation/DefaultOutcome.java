package org.radargun.stats.representation;

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
}

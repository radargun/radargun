package org.radargun.stats.representation;

import org.radargun.config.DefinitionElement;
import org.radargun.stats.OperationStats;

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

   @DefinitionElement(name = "requests", doc = "Retrieve number of operations executed.")
   public static class Requests extends RepresentationType {
      @Override
      public double getValue(OperationStats stats, long duration) {
         DefaultOutcome defaultOutcome = stats.getRepresentation(DefaultOutcome.class);
         if (defaultOutcome == null) throw new IllegalArgumentException("Cannot retrieve number of requests from " + stats);
         return defaultOutcome.requests;
      }
   }

   @DefinitionElement(name = "errors", doc = "Retrieve number of failed operations.")
   public static class Errors extends RepresentationType {
      @Override
      public double getValue(OperationStats stats, long duration) {
         DefaultOutcome defaultOutcome = stats.getRepresentation(DefaultOutcome.class);
         if (defaultOutcome == null) throw new IllegalArgumentException("Cannot retrieve number of failed requests from " + stats);
         return defaultOutcome.errors;
      }
   }

   /* Note: response-time-mean is retrieved through {@link org.radargun.stats.representation.MeanAndDev.Mean} */

   @DefinitionElement(name = "response-time-max", doc = "Retrieve maximum response time.")
   public static class ResponseTimeMax extends RepresentationType {
      @Override
      public double getValue(OperationStats stats, long duration) {
         DefaultOutcome defaultOutcome = stats.getRepresentation(DefaultOutcome.class);
         if (defaultOutcome == null) throw new IllegalArgumentException("Cannot retrieve number of failed requests from " + stats);
         return defaultOutcome.responseTimeMax;
      }
   }
}

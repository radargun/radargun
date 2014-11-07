package org.radargun.stats.representation;

import org.radargun.config.DefinitionElement;
import org.radargun.stats.OperationStats;

/**
 * Mean and standard deviation.
 *
 * @author Radim Vansa &lt;rvansa@redhat.com&gt;
 */
public final class MeanAndDev {
   public final double mean;
   public final double dev;

   public MeanAndDev(double mean, double dev) {
      this.mean = mean;
      this.dev = dev;
   }

   @DefinitionElement(name = "response-time-mean", doc = "Retrieve mean response time.")
   public static class Mean extends RepresentationType {
      @Override
      public double getValue(OperationStats stats, int threads, long duration) {
         MeanAndDev md = stats.getRepresentation(MeanAndDev.class);
         if (md == null) throw new IllegalArgumentException("Cannot retrieve mean from " + stats);
         return md.mean;
      }
   }

   @DefinitionElement(name = "response-time-deviation", doc = "Retrieve response time deviation.")
   public static class Deviation extends RepresentationType {
      @Override
      public double getValue(OperationStats stats, int threads, long duration) {
         MeanAndDev md = stats.getRepresentation(MeanAndDev.class);
         if (md == null) throw new IllegalArgumentException("Cannot retrieve mean from " + stats);
         return md.dev;
      }
   }
}

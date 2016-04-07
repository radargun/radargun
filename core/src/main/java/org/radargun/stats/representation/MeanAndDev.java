package org.radargun.stats.representation;

import org.radargun.config.DefinitionElement;
import org.radargun.stats.Statistics;

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
      public double getValue(Statistics statistics, String operation, long duration) {
         MeanAndDev md = statistics.getRepresentation(operation, MeanAndDev.class);
         if (md == null) throw new IllegalArgumentException("Cannot retrieve mean from " + operation);
         return md.mean;
      }
   }

   @DefinitionElement(name = "response-time-deviation", doc = "Retrieve response time deviation.")
   public static class Deviation extends RepresentationType {
      @Override
      public double getValue(Statistics statistics, String operation, long duration) {
         MeanAndDev md = statistics.getRepresentation(operation, MeanAndDev.class);
         if (md == null) throw new IllegalArgumentException("Cannot retrieve mean from " + operation);
         return md.dev;
      }
   }

   public static class Series extends AbstractSeries<MeanAndDev> {
      static {
         AbstractSeries.register(Series.class, MeanAndDev.class);
      }

      public Series(long startTime, long period, MeanAndDev[] samples) {
         super(startTime, period, samples);
      }
   }
}

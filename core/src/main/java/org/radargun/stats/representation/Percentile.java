package org.radargun.stats.representation;

import org.radargun.config.DefinitionElement;
import org.radargun.config.Property;
import org.radargun.stats.Statistics;
import org.radargun.utils.Utils;

/**
 * Representation used for retrieving value with certain percentile of occurence,
 * e.g. for data set 1, 1, 1, 2, 3 and percentile = 60 the responseTimeMax should be 1
 * (as 60 % of the data set are <= 1) and for percentile 70 it is 2.
 * It is expected that when calling {@link org.radargun.stats.OperationStats#getRepresentation(Class, Statistics, Object...)}
 * with Percentile as the class argument, first argument is double value between 0 and 1 (inclusive).
 *
 * @author Radim Vansa &lt;rvansa@redhat.com&gt;
 */
public class Percentile {
   public final double responseTimeMax;

   public Percentile(double responseTimeMax) {
      this.responseTimeMax = responseTimeMax;
   }

   public static double getPercentile(Object[] args) {
      double percentile = Utils.getArg(args, 0, Double.class);
      if (percentile < 0 || percentile > 100) throw new IllegalArgumentException(String.valueOf(percentile));
      return percentile;
   }

   @DefinitionElement(name = "percentile", doc = "Retrieve max response time at given percentile.")
   public static class PercentileAt extends RepresentationType {
      @Property(doc = "Percentile value, between 0 and 100.", optional = false)
      protected double value;

      @Override
      public double getValue(Statistics statistics, String operation, long duration) {
         Percentile percentile = statistics.getRepresentation(operation, Percentile.class, value);
         if (percentile == null) throw new IllegalArgumentException("Cannot retrieve percentile from " + operation);
         return percentile.responseTimeMax;
      }
   }
}

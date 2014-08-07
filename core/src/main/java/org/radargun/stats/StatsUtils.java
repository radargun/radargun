package org.radargun.stats;

import java.util.List;

/**
 * Utilities for statistics
 *
 * @author Matej Cimbora &lt;mcimbora@redhat.com&gt;
 */
public class StatsUtils {

   private StatsUtils() {
   }

   /**
    * Calculates maximum relative difference (MRD)
    *
    * @param values input values
    * @return MRD
    */
   public static double calculateMrd(List<Double> values) {
      double max = 0d;
      double avg = getAverage(values);
      for (Double d : values) {
         double relDiff = (Math.abs(avg - d) / avg) * 100d;
         if (relDiff > max) {
            max = relDiff;
         }
      }
      return max;
   }

   private static double getAverage(List<Double> values) {
      double sum = 0d;
      for (Double d : values) {
         sum += d;
      }
      return sum / values.size();
   }
}

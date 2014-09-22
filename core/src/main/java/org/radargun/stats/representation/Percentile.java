package org.radargun.stats.representation;

import java.util.Arrays;

/**
 * Representation used for retrieving value with certain percentile of occurence,
 * e.g. for data set 1, 1, 1, 2, 3 and percentile = 60 the responseTimeMax should be 1
 * (as 60 % of the data set are <= 1) and for percentile 70 it is 2.
 * It is expected that when calling {@link org.radargun.stats.OperationStats#getRepresentation(Class, Object...)}
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
      if (args == null || args.length != 1 || !(args[0] instanceof Double)) throw new IllegalArgumentException(Arrays.toString(args));
      double percentile = (Double) args[0];
      if (percentile < 0 || percentile > 100) throw new IllegalArgumentException(String.valueOf(percentile));
      return percentile;
   }
}

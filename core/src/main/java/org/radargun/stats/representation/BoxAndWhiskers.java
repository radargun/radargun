package org.radargun.stats.representation;

/**
 * --- <- max regular value
 *  |
 * --- <- 75 % values are less than this (q3)
 * | |
 * |o| <- mean
 * | |
 * --- <- 25 % values are less than this (q1)
 *  |
 * --- <- min regular value
 *
 * No cats whatsoever.
 *
 * @author Radim Vansa &lt;rvansa@redhat.com&gt;
 */
public final class BoxAndWhiskers {
   public final double maxRegular;
   public final double q3;
   public final double mean;
   public final double q1;
   public final double minRegular;

   public BoxAndWhiskers(double maxRegular, double q3, double mean, double q1, double minRegular) {
      this.maxRegular = maxRegular;
      this.q3 = q3;
      this.mean = mean;
      this.q1 = q1;
      this.minRegular = minRegular;
   }
}

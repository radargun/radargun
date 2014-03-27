package org.radargun.stats.representation;

/**
* @author Radim Vansa &lt;rvansa@redhat.com&gt;
*/
public final class MeanAndDev {
   public final double mean;
   public final double dev;

   public MeanAndDev(double mean, double dev) {
      this.mean = mean;
      this.dev = dev;
   }
}

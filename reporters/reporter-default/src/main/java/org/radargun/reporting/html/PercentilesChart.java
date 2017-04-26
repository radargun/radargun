package org.radargun.reporting.html;

import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.LongStream;

import org.radargun.stats.representation.Histogram;

/**
 * Chart showing inverse form of the histogram with focus on higher percentiles
 *
 * @author Radim Vansa &lt;rvansa@redhat.com&gt;
 */
public class PercentilesChart {
   List<String> names = new ArrayList<>();
   List<double[]> percentiles = new ArrayList<>();
   List<double[]> max = new ArrayList<>();
   List<double[]> avg = new ArrayList<>();

   public int size() {
      return names.size();
   }

   public String name(int index) {
      return names.get(index);
   }

   private static String join(double[] a) {
      StringBuilder sb = new StringBuilder(a.length * 8);
      DecimalFormat format = new DecimalFormat();
      format.setMaximumFractionDigits(4);
      if (a.length != 0) sb.append(format.format(a[0]));
      for (int i = 1; i < a.length; ++i) sb.append(", ").append(format.format(a[i]));
      return sb.toString();
   }

   /**
    * Percentiles for values, where each value is transformed as -log10(1 - p)
    */
   public String percentiles(int index) {
      return join(percentiles.get(index));
   }

   /**
    * All values are log10(Math.max(0.001, value))ed
    */
   public String max(int index) {
      return join(max.get(index));
   }

   /**
    * All values are log10(Math.max(0.001, value))ed
    */
   public String avg(int index) {
      return join(avg.get(index));
   }

   /**
    * @param seriesName Name of the plotted operation
    * @param histogram Histogram that should be plotted
    */
   public void addHistogram(String seriesName, Histogram histogram) {
      names.add(seriesName);

      long totalCount = 0;
      for (long count : histogram.counts) {
         totalCount += count;
      }
      if (totalCount == 0) {
         percentiles.add(new double[] {});
         max.add(new double[] {});
         avg.add(new double[] {});
         return;
      }

      long accCount = 0;
      long sumResponseTime = 0;
      int length = (int) LongStream.of(histogram.counts).filter(c -> c != 0).count();
      double[] percentiles = new double[length];
      double[] max = new double[length];
      double[] avg = new double[length];

      double limit = 2;
      int j = 0;
      for (int i = 0; i < histogram.counts.length; i++) {
         if (histogram.counts[i] == 0) continue;
         sumResponseTime += histogram.counts[i] * (histogram.ranges[i] + histogram.ranges[i + 1]) / 2;
         accCount += histogram.counts[i];
         if (accCount != totalCount) {
            double p = limit = Math.floor(-100 * Math.log10(1 - (double) accCount / totalCount))/ 100;
            // omit invisible data points
            if (j != 0 && p == percentiles[j - 1]) continue;
            percentiles[j] = p;
         } else {
            percentiles[j] = limit + 0.1;
         }
         max[j] = Math.log10(histogram.ranges[i + 1]);
         avg[j] = Math.log10((double) sumResponseTime / accCount);
         ++j;
      }
      if (j < length) {
         percentiles = Arrays.copyOf(percentiles, j);
         max = Arrays.copyOf(max, j);
         avg = Arrays.copyOf(avg, j);
      }
      this.percentiles.add(percentiles);
      this.max.add(max);
      this.avg.add(avg);
   }
}

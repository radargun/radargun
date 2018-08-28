package org.radargun.reporting.html;

import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.IntStream;
import java.util.stream.LongStream;

import org.radargun.stats.representation.Histogram;

/**
 * Writes bar plot with time (in nanoseconds) on logarithmic X-axis and percents on Y-axis
 *
 * @author Radim Vansa &lt;rvansa@redhat.com&gt;
 */
public class HistogramChart {
   private List<String> names = new ArrayList<>();
   private List<Histogram> histograms = new ArrayList<>();
   private List<double[]> percents = new ArrayList<>();
   private double[] times;
   private double quotient;

   public int size() {
      return names.size();
   }

   public String quotient() {
      return String.valueOf(quotient);
   }

   private static String join(double[] a) {
      StringBuilder sb = new StringBuilder(a.length * 8);
      DecimalFormat format = new DecimalFormat();
      format.setMaximumFractionDigits(4);
      if (a.length != 0) sb.append(format.format(a[0]));
      for (int i = 1; i < a.length; ++i) sb.append(", ").append(format.format(a[i]));
      return sb.toString();
   }

   public String name(int index) {
      return names.get(index);
   }

   public String times() {
      return join(times);
   }

   public String percents(int index) {
      return join(percents.get(index));
   }

   public void addHistogram(String name, Histogram histogram) {
      names.add(name);
      histograms.add(histogram);
   }

   public void process(int buckets, double percentile) {
      long min = histograms.stream().mapToLong(h -> h.ranges[0]).min().orElse(1);
      long max = histograms.stream().mapToLong(h -> findMaxRange(h, percentile)) .max().orElse(min + 1);

      double exponent = Math.pow((double) max / (double) min, 1d / buckets);
      times = new double[buckets];
      double minLog = Math.log10(min);
      double exponentLog = Math.log10(exponent);
      times = IntStream.range(1, buckets + 2).mapToDouble(i -> minLog + (i - 0.5) * exponentLog).toArray();
      quotient = Math.log10(Math.sqrt(exponent));

      for (Histogram h : histograms) {
         long totalCount = LongStream.of(h.counts).sum();
         if (totalCount == 0) {
            percents.add(new double[0]);
         } else {
            double[] percents = new double[buckets];

            long accCount = 0;
            double current = min * exponent;
            int j = 0;
            for (int i = 1; i < h.ranges.length; ++i) {
               if (h.ranges[i] >= current) {
                  percents[j] = (double) accCount / (double) totalCount;
                  if (++j >= buckets) {
                     break;
                  }
                  current *= exponent;
                  accCount = h.counts[i - 1];
               } else {
                  accCount += h.counts[i - 1];
               }
            }
            if (j < buckets) {
               percents[j] = (double) accCount / (double) totalCount;
            }
            this.percents.add(percents);
         }
      }
   }

   private static long findMaxRange(Histogram h, double percentile) {
      long threshold = (long) Math.ceil(percentile * LongStream.of(h.counts).sum());
      long acc = 0;
      for (int i = 0; i < h.counts.length; ++i) {
         acc += h.counts[i];
         if (acc >= threshold) {
            return h.ranges[i + 1];
         }
      }
      return h.ranges[h.ranges.length - 1];
   }
}

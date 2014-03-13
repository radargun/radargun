/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2013, Red Hat Middleware LLC, and individual contributors
 * as indicated by the @author tags. See the copyright.txt file in the
 * distribution for a full listing of individual contributors.
 *
 * This is free software; you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation; either version 2.1 of
 * the License, or (at your option) any later version.
 *
 * This software is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this software; if not, write to the Free
 * Software Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA
 * 02110-1301 USA, or see the FSF site: http://www.fsf.org.
 */

package org.radargun.charts;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.text.NumberFormat;

import org.jfree.chart.ChartFactory;
import org.jfree.chart.ChartUtilities;
import org.jfree.chart.JFreeChart;
import org.jfree.chart.axis.NumberAxis;
import org.jfree.chart.axis.NumberTickUnit;
import org.jfree.chart.axis.StandardTickUnitSource;
import org.jfree.chart.axis.TickUnit;
import org.jfree.chart.plot.PlotOrientation;
import org.jfree.chart.plot.XYPlot;
import org.jfree.data.xy.XYDataset;
import org.jfree.data.xy.XYSeries;
import org.jfree.data.xy.XYSeriesCollection;

/**
 * Writes bar plot with time (in nanoseconds) on logarithmic X-axis and percents on Y-axis
 *
 * @author Radim Vansa &lt;rvansa@redhat.com&gt;
 */
public class BarPlotGenerator {
   /**
    * @param operation Name of the plotted operation
    * @param ranges ranges[0] = min, ranges[ranges.length - 1] = max
    * @param counts counts[i] is number of entries with value >= ranges[i - 1] and < ranges[i]
    * @param reportDir
    * @param filename
    * @throws IOException
    */
   public static void generate(String operation, long[] ranges, long[] counts, String reportDir, String filename, int width, int height) throws IOException {
      XYSeries series = new XYSeries(operation + " response times");
      long totalCount = 0;
      for (long count : counts) {
         totalCount += count;
      }
      double left = Math.log10(ranges[0]);
      double right = Math.log10(ranges[ranges.length - 1]);

      for (int i = 0; i < counts.length; i++) {
         series.add(Math.log10(ranges[i]), (double) counts[i] / totalCount);
      }
      series.add(right, 0d);
      XYDataset dataset = new XYSeriesCollection(series);
      JFreeChart chart = ChartFactory.createXYStepAreaChart(null, "Response time", "Percentage", dataset,
                                                       PlotOrientation.VERTICAL, false, false, false);
      XYPlot plot = (XYPlot) chart.getPlot();
      NumberAxis d = (NumberAxis) plot.getDomainAxis();
      d.setRange(left, right);
      d.setStandardTickUnits(new HistoTickUnitSource());
      plot.setDomainAxis(d);
      FileOutputStream output = new FileOutputStream(new File(reportDir + File.separator + filename));
      ChartUtilities.writeChartAsPNG(output, chart, width, height);
      output.close();
   }

   private static class HistoTickUnitSource extends StandardTickUnitSource {

      public TickUnit getLargerTickUnit(TickUnit unit) {
         double higher = Math.ceil(unit.getSize());
         return new HistoTickUnit(higher, NumberFormat.getNumberInstance());
      }

      public TickUnit getCeilingTickUnit(TickUnit unit) {
         return getLargerTickUnit(unit);
      }

      public TickUnit getCeilingTickUnit(double size) {
         double higher = Math.ceil(size);
         return new HistoTickUnit(higher, NumberFormat.getNumberInstance());
      }
   }

   private static class HistoTickUnit extends NumberTickUnit {

      public HistoTickUnit(double size, NumberFormat format) {
         super(size, format);
      }

      @Override
      public String valueToString(double value) {
         value = Math.pow(10d, value);
         String unit;
         if (value < 1000d) {
            unit = "ns";
         } else if (value < 1000000d) {
            unit = "us";
            value /= 1000d;
         } else {
            unit = "ms";
            value /= 1000000d;
         }
         return String.format("%.0f %s", value, unit);
      }

   }

}

package org.radargun.reporting.html;

import org.jfree.chart.ChartFactory;
import org.jfree.chart.JFreeChart;
import org.jfree.chart.plot.PlotOrientation;
import org.jfree.chart.plot.XYPlot;
import org.jfree.data.Range;
import org.jfree.data.xy.XYSeries;
import org.jfree.data.xy.XYSeriesCollection;
import org.radargun.stats.representation.Histogram;

/**
 * Chart showing inverse form of the histogram with focus on higher percentiles
 */
public class PercentilesChart extends Chart {
   private XYSeriesCollection dataset = new XYSeriesCollection();
   private double limit = 0.99; // minimum value
   private double lowerBound = Double.MAX_VALUE;
   private double upperBound = Double.MIN_VALUE;

   /**
    * @param seriesName Name of the plotted operation
    * @param histogram Histogram that should be plotted
    */
   public PercentilesChart addSeries(String seriesName, Histogram histogram) {
      XYSeries percentileSeries = new XYSeries(seriesName + ": max response time");
      XYSeries responseTimeSeries = new XYSeries(seriesName + ": average response time");
      long totalCount = 0;
      for (long count : histogram.counts) {
         totalCount += count;
      }
      limit = Math.max(limit, 1 - 1 / (double) totalCount);

      long accCount = 0;
      long sumResponseTime = 0;
      percentileSeries.add(0, 0);
      for (int i = 0; i < histogram.counts.length; i++) {
         sumResponseTime += histogram.counts[i] * (histogram.ranges[i] + histogram.ranges[i + 1]) / 2;
         accCount += histogram.counts[i];
         long value = histogram.ranges[i + 1];
         double percentile = Math.min((double) accCount / totalCount, limit);
         double averageResponseTime = (double) sumResponseTime / accCount;

         if (histogram.counts[i] > 0) {
            if (value > 0 && value < lowerBound) lowerBound = value;
            if (averageResponseTime > 0 && averageResponseTime < lowerBound) lowerBound = averageResponseTime;
            if (value > upperBound) upperBound = value;
         }

         percentileSeries.add(percentile, value);
         responseTimeSeries.add(percentile, averageResponseTime);
      }
      dataset.addSeries(percentileSeries);
      dataset.addSeries(responseTimeSeries);
      return this;
   }

   protected JFreeChart createChart() {
      JFreeChart chart = ChartFactory.createXYLineChart(null, "Percentage", "Response time", dataset,
         PlotOrientation.VERTICAL, true, false, false);
      XYPlot plot = (XYPlot) chart.getPlot();
      PercentileLogAxis xAxis = new PercentileLogAxis("Percentiles", limit);
      xAxis.setRange(0, limit);
      plot.setDomainAxis(xAxis);
      final LogTimeAxis yAxis = new LogTimeAxis("Response time", 0, new int[] {2, 5});
      if (lowerBound < upperBound) yAxis.setRange(new Range(lowerBound, upperBound * 1.1));
      plot.setRangeAxis(yAxis);
      return chart;
   }
}

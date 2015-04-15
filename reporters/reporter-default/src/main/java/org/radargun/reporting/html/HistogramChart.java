package org.radargun.reporting.html;

import org.jfree.chart.ChartFactory;
import org.jfree.chart.JFreeChart;
import org.jfree.chart.plot.PlotOrientation;
import org.jfree.chart.plot.XYPlot;
import org.jfree.data.xy.XYDataset;
import org.jfree.data.xy.XYSeries;
import org.jfree.data.xy.XYSeriesCollection;
import org.radargun.stats.representation.Histogram;

/**
 * Writes bar plot with time (in nanoseconds) on logarithmic X-axis and percents on Y-axis
 *
 * @author Radim Vansa &lt;rvansa@redhat.com&gt;
 */
public class HistogramChart extends Chart {
   private double left;
   private double right;
   private XYDataset dataset;

   /**
    * @param operation Name of the plotted operation
    * @param histogram Histogram that should be plotted
    */
   public HistogramChart setData(String operation, Histogram histogram)  {
      XYSeries series = new XYSeries(operation + " response times");
      long totalCount = 0;
      for (long count : histogram.counts) {
         totalCount += count;
      }
      left = histogram.ranges[0];
      right = histogram.ranges[histogram.ranges.length - 1];

      for (int i = 0; i < histogram.counts.length; i++) {
         series.add(histogram.ranges[i], (double) histogram.counts[i] / totalCount);
      }
      series.add(right, 0d);
      dataset = new XYSeriesCollection(series);
      return this;
   }

   protected JFreeChart createChart() {
      JFreeChart chart = ChartFactory.createXYStepAreaChart(null, "Response time", "Percentage", dataset,
                                                       PlotOrientation.VERTICAL, false, false, false);
      XYPlot plot = (XYPlot) chart.getPlot();
      LogTimeAxis xAxis = new LogTimeAxis("Response time");
      xAxis.setRange(left, right);
      plot.setDomainAxis(xAxis);
      return chart;
   }

}

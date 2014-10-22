package org.radargun.reporting.html;

import org.jfree.chart.ChartFactory;
import org.jfree.chart.JFreeChart;
import org.jfree.chart.plot.PlotOrientation;
import org.jfree.chart.renderer.category.StatisticalBarRenderer;
import org.jfree.data.statistics.DefaultStatisticalCategoryDataset;

/**
 * Chart showing the values as vertical bars.
 *
 * @author Mircea Markus &lt;Mircea.Markus@jboss.com&gt;
 */
public class BarChart extends ComparisonChart {

   protected final DefaultStatisticalCategoryDataset categorySet = new DefaultStatisticalCategoryDataset();
   protected boolean hasDeviations = false;

   public BarChart(String domainLabel, String rangeLabel) {
      super(domainLabel, rangeLabel);
   }

   @Override
   protected JFreeChart createChart() {
      JFreeChart chart = ChartFactory.createBarChart(null, domainLabel, rangeLabel,
            categorySet, PlotOrientation.VERTICAL, true, false, false);
      if (hasDeviations) {
         chart.getCategoryPlot().setRenderer(new StatisticalBarRenderer());
      }
      return chart;
   }

   @Override
   public void addValue(double value, double deviation, Comparable seriesName, double xValue, String xString) {
      hasDeviations = deviation != 0;
      categorySet.add(value, deviation, seriesName, xString);
   }
}
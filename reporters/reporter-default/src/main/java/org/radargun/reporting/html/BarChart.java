package org.radargun.reporting.html;

import org.jfree.chart.ChartFactory;
import org.jfree.chart.JFreeChart;
import org.jfree.chart.plot.PlotOrientation;
import org.jfree.chart.renderer.category.StatisticalBarRenderer;

/**
 * Chart showing the values as vertical bars.
 *
 * @author Mircea Markus &lt;Mircea.Markus@jboss.com&gt;
 */
public class BarChart extends ComparisonChart {

   public BarChart(String domainLabel, String rangeLabel) {
      super(domainLabel, rangeLabel);
   }

   @Override
   protected JFreeChart createChart() {
      JFreeChart chart = ChartFactory.createBarChart(null, domainLabel, rangeLabel,
            categorySet, PlotOrientation.VERTICAL, true, false, false);
      chart.getCategoryPlot().setRenderer(new StatisticalBarRenderer());
      return chart;
   }
}
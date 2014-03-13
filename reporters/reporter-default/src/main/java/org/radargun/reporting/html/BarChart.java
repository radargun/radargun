package org.radargun.reporting.html;

import org.jfree.chart.ChartFactory;
import org.jfree.chart.JFreeChart;
import org.jfree.chart.plot.PlotOrientation;
import org.jfree.chart.renderer.category.StatisticalBarRenderer;

/**
 * @author Mircea Markus
 */
public class BarChart extends ComparisonChart {
   @Override
   protected JFreeChart createChart() {
      JFreeChart chart = ChartFactory.createBarChart(null, "Cluster size", "Response time (ms)",
            categorySet, PlotOrientation.VERTICAL, true, false, false);
      chart.getCategoryPlot().setRenderer(new StatisticalBarRenderer());
      return chart;
   }
}
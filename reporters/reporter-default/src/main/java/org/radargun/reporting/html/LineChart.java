package org.radargun.reporting.html;

import org.jfree.chart.ChartFactory;
import org.jfree.chart.JFreeChart;
import org.jfree.chart.plot.PlotOrientation;
import org.jfree.chart.renderer.category.StatisticalLineAndShapeRenderer;

/**
 * @author Mircea Markus
 */
public class LineChart extends ComparisonChart {

   private final String domainLabel;

   public LineChart(String domainLabel) {
      this.domainLabel = domainLabel;
   }

   @Override
   protected JFreeChart createChart() {
      JFreeChart chart = ChartFactory.createLineChart(null, domainLabel, "Response time (ms)",
            categorySet, PlotOrientation.VERTICAL, true, false, false);
      chart.getCategoryPlot().setRenderer(new StatisticalLineAndShapeRenderer());
      return chart;
   }
}
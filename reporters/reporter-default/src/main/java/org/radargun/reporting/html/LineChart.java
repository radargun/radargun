package org.radargun.reporting.html;

import org.jfree.chart.ChartFactory;
import org.jfree.chart.JFreeChart;
import org.jfree.chart.plot.PlotOrientation;
import org.jfree.chart.renderer.category.StatisticalLineAndShapeRenderer;

/**
 * Chart showing the values as connected lines.
 *
 * @author Mircea Markus &lt;Mircea.Markus@jboss.com&gt;
 */
public class LineChart extends ComparisonChart {

   public LineChart(String domainLabel, String rangeLabel) {
      super(domainLabel, rangeLabel);
   }

   @Override
   protected JFreeChart createChart() {
      JFreeChart chart = ChartFactory.createLineChart(null, domainLabel, rangeLabel,
            categorySet, PlotOrientation.VERTICAL, true, false, false);
      chart.getCategoryPlot().setRenderer(new StatisticalLineAndShapeRenderer());
      return chart;
   }
}
package org.radargun.reporting;

import java.io.File;
import java.io.IOException;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.jfree.chart.ChartFactory;
import org.jfree.chart.ChartUtilities;
import org.jfree.chart.JFreeChart;
import org.jfree.chart.plot.PlotOrientation;
import org.jfree.chart.renderer.category.StatisticalBarRenderer;
import org.jfree.chart.renderer.category.StatisticalLineAndShapeRenderer;
import org.jfree.chart.title.TextTitle;
import org.radargun.utils.Utils;

/**
 * @author Mircea Markus
 */
public class ComparisonChartGenerator {

   private static Log log = LogFactory.getLog(ComparisonChartGenerator.class);

   public static void generate(ClusterReport report, String reportDir, String fileName, int width, int height) throws IOException {
      File root = new File(reportDir);
      if (!root.exists()) {
         if (!root.mkdirs()) {
            log.warn("Could not create root dir : " + root.getAbsolutePath() + " This might result in reports not being generated");
         } else {
            log.info("Created root file: " + root);
         }
      }
      File chartFile = new File(root, fileName + ".png");
      Utils.backupFile(chartFile);

      report.sort();
      ChartUtilities.saveChartAsPNG(chartFile, createChart(report), width, height);

      log.info("Chart saved as " + chartFile);
   }
      
   private static JFreeChart createChart(ClusterReport report) {
      JFreeChart chart;
      if (report.getCategorySet().getColumnCount() > 1) {
         chart = ChartFactory.createLineChart(report.getTitle(), report.getXLabel(), report.getYLabel(),
               report.getCategorySet(), PlotOrientation.VERTICAL, true, false, false);
         chart.getCategoryPlot().setRenderer(new StatisticalLineAndShapeRenderer());
      } else {
         chart = ChartFactory.createBarChart(report.getTitle(), report.getXLabel(), report.getYLabel(),
               report.getCategorySet(), PlotOrientation.VERTICAL, true, false, false);
         chart.getCategoryPlot().setRenderer(new StatisticalBarRenderer());
      }

      if (report.getSubtitle() != null) {
         chart.addSubtitle(new TextTitle(report.getSubtitle()));
      }
      return chart;
   }
}
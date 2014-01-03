package org.radargun.reporting;

import java.io.File;
import java.io.IOException;

import org.radargun.logging.Log;
import org.radargun.logging.LogFactory;
import org.jfree.chart.ChartFactory;
import org.jfree.chart.ChartUtilities;
import org.jfree.chart.JFreeChart;
import org.radargun.utils.Utils;

/**
 * This class generates a TimeSeries chart appropriate for the AbstractActivityMonitor subclasses
 * 
 * @author Alan Field &lt;afield@redhat.com&gt;
 */
public class TimeSeriesReportGenerator {

   private static Log log = LogFactory.getLog(TimeSeriesReportGenerator.class);

   public static void generate(ClusterTimeSeriesReport report, String reportDir, String fileName) throws IOException {
      File root = new File(reportDir);
      if (!root.exists()) {
         if (!root.mkdirs()) {
            log.warn("Could not create root dir : " + root.getAbsolutePath()
                  + " This might result in reports not being generated");
         } else {
            log.info("Created root file: " + root);
         }
      }
      File chartFile = new File(root, fileName + ".png");
      Utils.backupFile(chartFile);

      ChartUtilities.saveChartAsPNG(chartFile, createChart(report), 1024, 768);

      log.info("Chart saved as " + chartFile);
   }

   private static JFreeChart createChart(ClusterTimeSeriesReport report) {
      return ChartFactory.createTimeSeriesChart(report.getTitle(), report.getXLabel(), report.getYLabel(),
            report.getCategorySet(), true, false, false);
   }
}
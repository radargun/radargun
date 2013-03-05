package org.radargun.reporting;

import java.awt.Color;
import java.io.File;
import java.io.IOException;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.jfree.chart.ChartFactory;
import org.jfree.chart.ChartUtilities;
import org.jfree.chart.JFreeChart;
import org.jfree.chart.title.TextTitle;
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
      JFreeChart chart = ChartFactory.createTimeSeriesChart(report.getTitle(), report.getXLabel(), report.getYLabel(),
            report.getCategorySet(), true, false, false);
      chart.addSubtitle(new TextTitle(report.getSubtitle()));
      chart.setBorderVisible(true);
      chart.setAntiAlias(true);
      chart.setTextAntiAlias(true);
      chart.setBackgroundPaint(new Color(0x61, 0x9e, 0xa1));
      return chart;
   }
}
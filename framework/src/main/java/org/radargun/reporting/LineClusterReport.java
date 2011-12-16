package org.radargun.reporting;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.radargun.utils.Utils;
import org.jfree.chart.ChartFactory;
import org.jfree.chart.ChartUtilities;
import org.jfree.chart.JFreeChart;
import org.jfree.chart.plot.PlotOrientation;
import org.jfree.chart.title.TextTitle;
import org.jfree.data.category.DefaultCategoryDataset;

import java.awt.*;
import java.io.File;
import java.util.Map;
import java.util.SortedMap;
import java.util.TreeMap;

/**
 * @author Mircea.Markus@jboss.com
 */
public class LineClusterReport implements ClusterReport {

   private static Log log = LogFactory.getLog(LineClusterReport.class);

   private DefaultCategoryDataset categorySet = new DefaultCategoryDataset();
   private String reportDir;
   private String fileName;
   private String xLabel;
   private String yLabel;
   private String title;
   private String subtitle;


   public void setReportFile(String reportDir, String fileName) {
      this.reportDir = reportDir;
      this.fileName = fileName;
   }

   public void init(String xLabels, String yLabels, String title, String subtitle) {
      this.xLabel = xLabels;
      this.yLabel = yLabels;
      this.title = title;
      this.subtitle = subtitle;
   }

   public void addCategory(String rowKey, int columnKey, Number value) {
      this.categorySet.addValue(value, rowKey, columnKey);
   }

   public void generate() throws Exception {
      sort();
      File root = new File(reportDir);
      if (!root.exists()) {
         if (root.mkdirs()) {
            log.warn("Could not create root dir : " + root.getAbsolutePath() + " This might result in reports not being generated");
         } else {
            log.info("Created root file: " + root);
         }
      }
      File chartFile = new File(root, fileName + ".png");
      Utils.backupFile(chartFile);

      ChartUtilities.saveChartAsPNG(chartFile, createChart(), 1024, 768);

      log.info("Chart saved as " + chartFile);
   }

   /**
    * Crappy that the JFeeChart data set doesn't order columns and rows by default or even as an option.  Need to do
    * this manually.
    */
   private void sort() {
      SortedMap<Comparable, SortedMap<Comparable, Number>> raw = new TreeMap<Comparable, SortedMap<Comparable, Number>>();
      for (int i = 0; i < categorySet.getRowCount(); i++) {
         Comparable row = categorySet.getRowKey(i);
         SortedMap<Comparable, Number> rowData = new TreeMap<Comparable, Number>();
         for (int j = 0; j < categorySet.getColumnCount(); j++) {
            Comparable column = categorySet.getColumnKey(j);
            Number value = categorySet.getValue(i, j);
            rowData.put(column, value);
         }
         raw.put(row, rowData);
      }

      categorySet.clear();
      for (Comparable row : raw.keySet()) {
         Map<Comparable, Number> rowData = raw.get(row);
         for (Comparable column : rowData.keySet()) {
            categorySet.addValue(rowData.get(column), row, column);
         }
      }
   }

   private JFreeChart createChart() {
      JFreeChart chart = ChartFactory.createLineChart(title, xLabel, yLabel, categorySet, PlotOrientation.VERTICAL, true, false, false);
      chart.addSubtitle(new TextTitle(subtitle));
      chart.setBorderVisible(true);
      chart.setAntiAlias(true);
      chart.setTextAntiAlias(true);
      chart.setBackgroundPaint(new Color(0x61, 0x9e, 0xa1));
      return chart;
   }
}

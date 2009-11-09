package org.cachebench.reportgenerators;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.commons.math.stat.descriptive.DescriptiveStatistics;
import org.apache.commons.math.stat.descriptive.SynchronizedDescriptiveStatistics;
import org.jfree.chart.ChartFactory;
import org.jfree.chart.ChartUtilities;
import org.jfree.chart.JFreeChart;
import org.jfree.chart.plot.PlotOrientation;
import org.jfree.data.category.CategoryDataset;
import org.jfree.data.category.DefaultCategoryDataset;

import java.awt.*;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.FilenameFilter;
import java.io.IOException;
import java.util.Map;
import java.util.SortedMap;
import java.util.StringTokenizer;
import java.util.TreeMap;

/**
 * Generates average and total throughput.  Used for parsing reports generated with a ClusterReportGenerator.
 *
 * @author Manik Surtani (<a href="mailto:manik@jboss.org">manik@jboss.org</a>)
 */
public class ThroughputChartGenerator extends AbstractChartGen
{
   private DefaultCategoryDataset averageThroughput, totalThroughput;
   private String chartNameAverage = "chart-averageThroughput.png", chartNameTotal = "chart-totalThroughput.png";
   private Log log = LogFactory.getLog(ThroughputChartGenerator.class);

   public void generateChart() throws IOException
   {
      readData();

      String chartAvgFileName = filenamePrefix == null ? chartNameAverage : filenamePrefix + "-" + chartNameAverage;
      File chartFile = new File(chartAvgFileName);
      if (chartFile.exists())
      {
         chartFile.renameTo(new File(chartAvgFileName + "." + System.currentTimeMillis()));
         chartFile = new File(chartAvgFileName);
      }

      ChartUtilities.saveChartAsPNG(chartFile, createChart(averageThroughput, "Report: Average throughput per cache instance", "Throughput per cache instance (reqs/sec)"), 1024, 768);

      String chartTotalFileName = filenamePrefix == null ? chartNameTotal : filenamePrefix + "-" + chartNameTotal;
      chartFile = new File(chartTotalFileName);
      if (chartFile.exists())
      {
         chartFile.renameTo(new File(chartTotalFileName + "." + System.currentTimeMillis()));
         chartFile = new File(chartTotalFileName);
      }

      ChartUtilities.saveChartAsPNG(chartFile, createChart(totalThroughput, "Report: Total throughput for cluster", "Overall throughput (reqs/sec)"), 1024, 768);

      System.out.println("Charts saved as " + chartAvgFileName + " and " + chartTotalFileName);
   }

   private JFreeChart createChart(CategoryDataset data, String title, String yLabel)
   {
      JFreeChart chart = ChartFactory.createLineChart(title, "Cluster size (number of cache instances)", yLabel, data, PlotOrientation.VERTICAL, true, false, false);
      chart.addSubtitle(getSubtitle());
      chart.setBorderVisible(true);
      chart.setAntiAlias(true);
      chart.setTextAntiAlias(true);
      chart.setBackgroundPaint(new Color(0x61, 0x9e, 0xa1));
      return chart;
   }

   private void readData() throws IOException
   {
      File file = new File(reportDirectory);
      if (!file.exists() || !file.isDirectory())
         throw new IllegalArgumentException("Report directory " + reportDirectory + " does not exist or is not a directory!");

      File[] files = file.listFiles(new FilenameFilter()
      {
         public boolean accept(File dir, String name)
         {
            return name.toUpperCase().endsWith(".CSV");
         }
      });

      averageThroughput = new DefaultCategoryDataset();
      totalThroughput = new DefaultCategoryDataset();
      for (File f : files)
      {
         readData(f);
      }

      sort(averageThroughput);
      sort(totalThroughput);
   }

   /**
    * Crappy that the JFReechart data set doesn't order columns and rows by default or even as an option.  Need to do this manually.
    *
    * @param data
    */
   private void sort(DefaultCategoryDataset data)
   {
      SortedMap<Comparable, SortedMap<Comparable, Number>> raw = new TreeMap<Comparable, SortedMap<Comparable, Number>>();
      for (int i = 0; i < data.getRowCount(); i++)
      {
         Comparable row = data.getRowKey(i);
         SortedMap<Comparable, Number> rowData = new TreeMap<Comparable, Number>();
         for (int j = 0; j < data.getColumnCount(); j++)
         {
            Comparable column = data.getColumnKey(j);
            Number value = data.getValue(i, j);
            rowData.put(column, value);
         }
         raw.put(row, rowData);
      }

      data.clear();
      for (Comparable row : raw.keySet())
      {
         Map<Comparable, Number> rowData = raw.get(row);
         for (Comparable column : rowData.keySet())
         {
            data.addValue(rowData.get(column), row, column);
         }
      }
   }


   private void readData(File f) throws IOException
   {
      log.info("Parsing file " + f.getAbsoluteFile());
      // chop up the file name to get productAndConfiguration and clusterSize.
      Integer clusterSize = 0;
      DescriptiveStatistics stats = new SynchronizedDescriptiveStatistics();
      // file name is in the format data_<cache-product>_<cache-cfg.xml>_<cluster-size>.csv

      StringTokenizer strtok = new StringTokenizer(f.getName(), "_");
      strtok.nextToken(); // this is the "data-" bit
      String productNameAndConfiguration = strtok.nextToken() + "(" + strtok.nextToken();
      // chop off the trailing ".xml"
      if (productNameAndConfiguration.toUpperCase().endsWith(".XML"))
         productNameAndConfiguration = productNameAndConfiguration.substring(0, productNameAndConfiguration.length() - 4);
      productNameAndConfiguration += ")";

      // cluster size
      String cS = strtok.nextToken();
      if (cS.toUpperCase().endsWith(".CSV")) cS = cS.substring(0, cS.length() - 4);
      clusterSize = Integer.parseInt(cS);

      // now read the data.
      String line = null;
      BufferedReader br = new BufferedReader(new FileReader(f));
      while ((line = br.readLine()) != null)
      {
         double throughput = getThroughput(line);
         if (throughput != -1) stats.addValue(throughput);
      }

      averageThroughput.addValue(stats.getMean(), productNameAndConfiguration, clusterSize);
      totalThroughput.addValue(stats.getSum(), productNameAndConfiguration, clusterSize);
   }

   private double getThroughput(String line)
   {
      // To be a valid line, the line should be comma delimited
      StringTokenizer strTokenizer = new StringTokenizer(line, ",");
      if (strTokenizer.countTokens() < 2) return -1;

      // we want the 3rd element which is throughput
      strTokenizer.nextToken();
      strTokenizer.nextToken();
      String candidate = strTokenizer.nextToken();
      try
      {
         return Double.parseDouble(candidate);
      }
      catch (NumberFormatException nfe)
      {
         return -1;
      }
   }

}

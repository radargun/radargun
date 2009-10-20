package org.cachebench.reportgenerators;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.jfree.chart.ChartFactory;
import org.jfree.chart.ChartUtilities;
import org.jfree.chart.JFreeChart;
import org.jfree.chart.labels.CategoryItemLabelGenerator;
import org.jfree.chart.plot.PlotOrientation;
import org.jfree.chart.renderer.category.BarRenderer3D;
import org.jfree.data.category.CategoryDataset;
import org.jfree.data.category.DefaultCategoryDataset;

import java.awt.*;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.FilenameFilter;
import java.io.IOException;
import java.text.NumberFormat;
import java.util.StringTokenizer;

/**
 * Generates average and total throughput.  Used for parsing reports generated with a ClusterReportGenerator.
 *
 * @author Manik Surtani (<a href="mailto:manik@jboss.org">manik@jboss.org</a>)
 */
public class PutGetChartGenerator extends AbstractChartGen
{
   private DefaultCategoryDataset putData, getData;
   private String chartExtension = ".png";
   private String putChartName = "PutChart", getChartName = "GetChart";
   private Log log = LogFactory.getLog(PutGetChartGenerator.class);
   private int nPuts = 0, nGets = 0;
   private int numFilesScanned = 0;

   public void generateChart() throws IOException
   {
      readData();

      String putChartNameToUse = filenamePrefix == null ? putChartName : filenamePrefix + "-" + putChartName;
      File chartFile = new File(putChartNameToUse + chartExtension);
      if (chartFile.exists())
      {
         chartFile.renameTo(new File(putChartNameToUse + "." + System.currentTimeMillis() + chartExtension));
         chartFile = new File(putChartNameToUse + chartExtension);
      }

      ChartUtilities.saveChartAsPNG(chartFile, createChart("Report: Comparing Cache PUT (WRITE) performance", putData, nPuts / numFilesScanned), 800, 800);

      String getChartNameToUse = filenamePrefix == null ? getChartName : filenamePrefix + "-" + getChartName;
      chartFile = new File(getChartNameToUse + chartExtension);
      if (chartFile.exists())
      {
         chartFile.renameTo(new File(getChartNameToUse + "." + System.currentTimeMillis() + chartExtension));
         chartFile = new File(getChartNameToUse + chartExtension);
      }

      ChartUtilities.saveChartAsPNG(chartFile, createChart("Report: Comparing Cache GET (READ) performance", getData, nGets / numFilesScanned), 800, 800);

      System.out.println("Charts saved as " + putChartNameToUse + " and " + getChartNameToUse);
   }

   private JFreeChart createChart(String title, DefaultCategoryDataset data, int numOperations)
   {
      JFreeChart chart = ChartFactory.createBarChart3D(title, "Cache operations performed (approx): " + NumberFormat.getIntegerInstance().format(numOperations), "Average time (" + MU + "-seconds)", data, PlotOrientation.VERTICAL, true, true, false);
      BarRenderer3D renderer = (BarRenderer3D) chart.getCategoryPlot().getRenderer();
      renderer.setBaseItemLabelsVisible(true);

      final NumberFormat fmt = NumberFormat.getNumberInstance();
      fmt.setMaximumFractionDigits(2);
      fmt.setMinimumFractionDigits(2);

      renderer.setBaseItemLabelGenerator(new CategoryItemLabelGenerator()
      {

         public String generateRowLabel(CategoryDataset categoryDataset, int i)
         {
            return null;
         }

         public String generateColumnLabel(CategoryDataset categoryDataset, int i)
         {
            return null;
         }

         public String generateLabel(CategoryDataset categoryDataset, int product, int operation)
         {
            String retval;
            try
            {
               retval = fmt.format(categoryDataset.getValue(product, operation)) + " " + MU + "s";
            }
            catch (Exception e)
            {
               e.printStackTrace();
               retval = e.toString();
            }
            return retval;
         }
      });

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

      putData = new DefaultCategoryDataset();
      getData = new DefaultCategoryDataset();

      for (File f : files)
      {
         readData(f);
      }

      //sort(averageThroughput);
   }

   private void readData(File f) throws IOException
   {
      log.debug("Processing file " + f);
      String productName = f.getName();
      if (productName.startsWith("data_"))
      {
         productName = productName.substring(5);
      }

      if (productName.indexOf(".xml") > 0)
      {
         productName = productName.substring(0, productName.indexOf(".xml"));
      }

      // now the contects of this file:

      String line = null;
      BufferedReader br = new BufferedReader(new FileReader(f));
      double avgPut = -1, avgGet = -1;
      Stats s = null;

      while ((line = br.readLine()) != null && s == null)
      {
         s = getAveragePutAndGet(line);
         log.debug("Read stats " + s);
         if (s != null)
         {
            avgPut = s.avgPut;
            avgGet = s.avgGet;
            nGets += s.numGets;
            nPuts += s.numPuts;
         }
      }

      br.close();

      putData.addValue(avgPut, productName, "PUT");
      getData.addValue(avgGet, productName, "GET");
      numFilesScanned++;
   }

   private Stats getAveragePutAndGet(String line)
   {
      // To be a valid line, the line should be comma delimited
      StringTokenizer strTokenizer = new StringTokenizer(line, ",");
      if (strTokenizer.countTokens() < 7) return null;

      // 8th token is avg put
      // 9th token is avg get
      for (int i = 0; i < 7; i++) strTokenizer.nextToken();

      String putStr = strTokenizer.nextToken();
      String getStr = strTokenizer.nextToken();

      // 20 and 21st tokens are the num puts and num gets performed.

      for (int i = 0; i < 10; i++) strTokenizer.nextToken();

      String nPutStr = strTokenizer.nextToken();
      String nGetStr = strTokenizer.nextToken();

      Stats s = new Stats();
      try
      {
         s.avgPut = Double.parseDouble(putStr) / 1000;
         s.avgGet = Double.parseDouble(getStr) / 1000;
         s.numPuts = Integer.parseInt(nPutStr);
         s.numGets = Integer.parseInt(nGetStr);
      }
      catch (NumberFormatException nfe)
      {
//         log.error("Unable to parse file properly!", nfe);
         return null;
      }
      return s;
   }

   private static class Stats
   {
      double avgPut, avgGet;
      int numPuts, numGets;


      public String toString()
      {
         return "Stats{" +
               "avgPut=" + avgPut +
               ", avgGet=" + avgGet +
               ", numPuts=" + numPuts +
               ", numGets=" + numGets +
               '}';
      }
   }

}

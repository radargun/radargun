package org.radargun.reporting;

import sun.awt.image.OffScreenImage;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.FilenameFilter;
import java.io.IOException;
import java.util.Map;
import java.util.Properties;
import java.util.SortedMap;
import java.util.StringTokenizer;
import java.util.TreeMap;

/**
 * @author Mircea Markus
 */
public class LineReportGenerator {

   private static final String REPORT_PNG = "report";

   public static void main(String[] args) throws Exception {

      if (args.length == 0) {
         printHelpAndExit();
      }

      Properties props = new Properties();
      FileReader reader = new FileReader(args[0]);
      try {
         props.load(reader);
      } catch (IOException e) {
         System.err.println("Could not load the report descriptor:" + e.getMessage());
         printHelpAndExit();
      } finally {
         reader.close();
      }

      String dir = props.getProperty("csv-dir");
      String reportFile = props.getProperty("chart", REPORT_PNG);
      String title = props.getProperty("title");
      String xLabels = props.getProperty("x-label");
      String yLabels = props.getProperty("y-label");
      String subtitle = props.getProperty("subtitle");
      String items = props.getProperty("items");

      if (dir == null || title == null || yLabels == null || subtitle == null || items == null) {
         printHelpAndExit();
      }


      LineClusterReport lcr = new LineClusterReport();

      lcr.setReportFile(".", reportFile);

      lcr.init(xLabels, yLabels, title, subtitle);

      StringTokenizer st = new StringTokenizer(items, ":" );
      while (st.hasMoreElements()) {
         String item = st.nextToken().trim();
         System.out.println("st = " + item);
         int firstRoundBracket = item.indexOf("(");
         String fileNamePrefix = item.substring(0, firstRoundBracket);
         int indexOfComma = item.indexOf(",");
         Integer columnIndex = Integer.parseInt(item.substring(firstRoundBracket + 1, indexOfComma).trim());
         String itemNameInReport = item.substring(indexOfComma+1, item.indexOf(")"));
         SortedMap<Integer, Double> values = getValues(dir, columnIndex, fileNamePrefix);
         for (Map.Entry<Integer, Double> me : values.entrySet()) {
            lcr.addCategory(itemNameInReport, me.getKey(), me.getValue());
         }
      }

      lcr.generate();
   }

   private static SortedMap<Integer, Double> getValues(final String dir, final Integer columnIndex, final String fileNamePrefix) throws Exception {
      File f = new File(dir);
      String[] files = f.list(new FilenameFilter() {
         @Override
         public boolean accept(File file, String s) {
            return s.indexOf(fileNamePrefix) == 0;
         }
      });
      SortedMap<Integer, Double> result = new TreeMap<Integer, Double>();
      if (files == null) throw new RuntimeException("Could not find any files with prefix:" + fileNamePrefix + " in directory " + f.getAbsolutePath());
      for (String file : files) {
         FileReader reader = new FileReader(new File(dir, file));
         BufferedReader br = new BufferedReader(reader);
         br.readLine();//skip header
         Double sum = 0d;
         int count = 0;
         String line;
         while ((line = br.readLine()) != null) {
            StringTokenizer st = new StringTokenizer(line, ",");
            for (int i = 0; i <= columnIndex; i++) {
               if (!st.hasMoreElements()) throw new RuntimeException("No column with index " + columnIndex + " in file '" + fileNamePrefix + "'");
               String value = st.nextToken();
               if (i == columnIndex) {
                  sum += (Double.valueOf(value));
                  count++;
                  break;
               }
            }
         }
         int numNodes = Integer.parseInt(file.substring(fileNamePrefix.length(), file.indexOf(".")));
         result.put(numNodes, sum/count);
      }
      return result;
   }

   private static void printHelpAndExit() {
      System.out.println("Usage: ./chart.sh <chart-descriptor>");
      System.out.println("For an example of a chart descriptor look at <RADARGUN_HOME>/chart_descriptor.properties ");
      System.exit(1);
   }
}
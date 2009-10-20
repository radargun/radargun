package org.cachebench.reportgenerators.reportcentralizer;

import org.apache.commons.math.stat.descriptive.DescriptiveStatistics;

import java.io.File;
import java.io.IOException;
import java.io.BufferedReader;
import java.io.FileReader;
import java.util.StringTokenizer;

/**
 * @author Mircea.Markus@jboss.com
 * @since 2.2
 */
public class ReportData implements Comparable {

   /**
    * e.g. jbosscache-2.1.0
    */
   private String distribution;

   /**
    * e.g. pess-repl-async
    */
   private String configuration;
   private int clusterSize;

   private long avgReqPerSec;

   public ReportData() {
   }

   public ReportData(File reportFile) throws Exception {
      readData(reportFile);
   }

   private void readData(File f) throws IOException {
      processFileName(f.getName());
      // chop up the file name to get productAndConfiguration and clusterSize.
      // file name is in the format data_<cache-product>_<cache-cfg.xml>_<cluster-size>.csv

      // now read the data.
      String line = null;
      BufferedReader br = new BufferedReader(new FileReader(f));
      int goodlinesCount = 0;
      while ((line = br.readLine()) != null) {
         double throughput = getThroughput(line);
         if (throughput != -1)  {
            avgReqPerSec += throughput;
            goodlinesCount++;
         }
      }
      if (goodlinesCount < clusterSize)
      {
         throw new IllegalStateException("Number of line is not good for file: " + f.getName());
      }
      avgReqPerSec = avgReqPerSec / clusterSize;
   }

   private double getThroughput(String line) {
      // To be a valid line, the line should be comma delimited
      StringTokenizer strTokenizer = new StringTokenizer(line, ",");
      if (strTokenizer.countTokens() < 2) return -1;

      // we want the 3rd element which is throughput
      strTokenizer.nextToken();
      strTokenizer.nextToken();
      String candidate = strTokenizer.nextToken();
      try {
         return Double.parseDouble(candidate);
      }
      catch (NumberFormatException nfe) {
         return -1;
      }
   }


   public String getDistribution() {
      return distribution;
   }

   public String getConfiguration() {
      return configuration;
   }

   public int getClusterSize() {
      return clusterSize;
   }

   public long getAvgReqPerSec() {
      return avgReqPerSec;
   }

   /* e.g. data_jbosscache-2.1.0_pess-repl-async.xml_2.csv */
   public void processFileName(String name) {
      StringTokenizer strtok = new StringTokenizer(name, "_");
      strtok.nextToken(); // this is the "data-" bit
      distribution = strtok.nextToken(); /* jbosscache-2.1.0 */
      configuration = strtok.nextToken() /* pess-repl-async.xml */;
      // cluster size
      String cS = strtok.nextToken();
      if (cS.toUpperCase().endsWith(".CSV")) cS = cS.substring(0, cS.length() - 4);
      clusterSize = Integer.parseInt(cS);
   }

   public int compareTo(Object o) {
      ReportData other = (ReportData) o;
      return this.clusterSize - other.getClusterSize();
   }
}

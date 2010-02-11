package org.cachebench.stages;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.cachebench.reporting.ClusterReport;
import org.cachebench.reporting.LineClusterReport;
import org.cachebench.utils.Utils;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.FilenameFilter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.StringTokenizer;
import java.util.regex.Pattern;

/**
 * Stage that generates a chart from a set of csv files.
 * <pre>
 * - fnPrefix: the prefix of the generated chart file (png). No value by default, optional parameter.
 * - reportDirectory - where are the csv files located. Defaults to 'reports'
 * - outputDir - where to output the generated graphical reports. Defaults to 'reports'
 * </pre>
 *
 * @author Mircea.Markus@jboss.com
 */
public class GenerateChartStage extends AbstractMasterStage {

   private static Log log = LogFactory.getLog(GenerateChartStage.class);

   public static final String X_LABEL = "Cluster size (number of cache instances)";

   private String reportDirectory = "reports";
   private String csvFilesDirectory = "reports";
   private String fnPrefix;
   private Map<String, List<String>> filter = new HashMap<String, List<String>>();
   protected Map<String, List<Pattern>> compiledFilters = null;
   ClusterReport putReport = new LineClusterReport();
   ClusterReport getReport = new LineClusterReport();

   public boolean execute() throws Exception {
      putReport.setReportFile(reportDirectory, fnPrefix + "_PUT");
      putReport.init(X_LABEL, "PUT ops/sec on each cache instance", "Average PUT per cache instance", getSubtitle());
      getReport.setReportFile(reportDirectory, fnPrefix + "_GET");
      getReport.init(X_LABEL, "GET ops/sec on each cache instance", "Average GET per cache instance", getSubtitle());

      File[] files = getFilteredFiles(new File(csvFilesDirectory));
      for (File f : files) {
         readData(f);
      }

      putReport.generate();
      getReport.generate();
      return true;
   }

   private void readData(File f) throws IOException {
      log.debug("Processing file " + f);
      //expected file format is: <product>_<config>_<size>.csv
      StringTokenizer tokenizer = new StringTokenizer(Utils.fileName2Config(f.getName()), "_");
      String productName = tokenizer.nextToken();
      String configName = tokenizer.nextToken();
      int clusterSize = Integer.parseInt(tokenizer.nextToken());

      //now the contents of this file:
      String line;
      BufferedReader br = new BufferedReader(new FileReader(f));
      long avgPutPerSec = 0, avgGetsPerSec = 0;
      Stats s = null;
      br.readLine(); //this is the header
      while ((line = br.readLine()) != null) {
         s = getAveragePutAndGet(line);
         log.debug("Read stats " + s);
         if (s != null) {
            avgPutPerSec += s.putsPerSec;
            avgGetsPerSec += s.getsPerSec;
         }
      }
      br.close();
      avgGetsPerSec = avgGetsPerSec / clusterSize;
      avgPutPerSec = avgPutPerSec / clusterSize;

      String name = productName + "(" + configName + ")";
      putReport.addCategory(name, clusterSize, avgPutPerSec);
      getReport.addCategory(name, clusterSize, avgGetsPerSec);
   }

   private Stats getAveragePutAndGet(String line) {
      // To be a valid line, the line should be comma delimited
      StringTokenizer strTokenizer = new StringTokenizer(line, ",");
      if (strTokenizer.countTokens() < 7) return null;

      strTokenizer.nextToken();//skip index
      strTokenizer.nextToken();//skip duration
      strTokenizer.nextToken();//skip request per sec

      String getStr = strTokenizer.nextToken(); //this is get/sec
      String putStr = strTokenizer.nextToken(); //this is put/sec

      Stats s = new Stats();
      try {
         s.putsPerSec = Double.parseDouble(putStr);
         s.getsPerSec = Double.parseDouble(getStr);
      }
      catch (NumberFormatException nfe) {
         log.error("Unable to parse file properly!", nfe);
         return null;
      }
      return s;
   }


   public void setCsvFilesDirectory(String csvFilesDirectory) {
      this.csvFilesDirectory = csvFilesDirectory;
   }

   public void setFnPrefix(String fnPrefix) {
      this.fnPrefix = fnPrefix;
   }

   public void setReportDirectory(String reportDirectory) {
      this.reportDirectory = reportDirectory;
   }

   protected File[] getFilteredFiles(File file) {
      return file.listFiles(new FilenameFilter() {
         //accepted file names are <product-name>_<config-name>_<cluster-size>.csv
         public boolean accept(File dir, String name) {
            compileFilters();
            if (!name.toUpperCase().endsWith(".CSV")) {
               return false;
            }
            if (!isUsingFilters()) {
               return true;
            }
            StringTokenizer tokenizer = new StringTokenizer(name, "_");
            String productName = tokenizer.nextToken();
            String configName = tokenizer.nextToken();
            if (!filter.containsKey(productName)) return false;

            //first check that this works as compilation issues might have occurred during parsing the patterns
            if (filter.get(productName).contains(configName)) {
               return true;
            } else {
               if (configName.equals("*")) {
                  return true;
               } else {
                  List<Pattern> patternList = compiledFilters.get(productName);
                  for (Pattern pattern : patternList) {
                     if (pattern.matcher(configName).find()) {
                        log.trace("Pattern '" + pattern + "' matched config: " + configName);
                        return true;
                     }
                  }
               }
            }
            return false;
         }
      });
   }

   private void compileFilters() {
      if (compiledFilters == null) {
         compiledFilters = new HashMap<String, List<Pattern>>();
         for (String product : filter.keySet()) {
            List<Pattern> compiled = new ArrayList<Pattern>();
            for (String aFilter : filter.get(product)) {
               try {
                  Pattern pattern = Pattern.compile(aFilter);
                  compiled.add(pattern);
               } catch (Exception e) {
                  log.warn("Exception while compiling the pattern: '" + aFilter + "'", e);
               }
            }
            compiledFilters.put(product, compiled);
         }
      }
   }

   public boolean isUsingFilters() {
      return !filter.isEmpty();
   }


   public void addReportFilter(String prodName, String config) {
      List<String> stringList = filter.get(prodName);
      if (stringList == null) {
         stringList = new ArrayList<String>();
         filter.put(prodName, stringList);
      }
      stringList.add(Utils.fileName2Config(config));
   }

   public static String getSubtitle() {
      return "Generated on " + new Date() + " by The CacheBenchFwk\nJDK: " +
            System.getProperty("java.vm.name") + " (" + System.getProperty("java.vm.version") + ", " +
            System.getProperty("java.vm.vendor") + ") OS: " + System.getProperty("os.name") + " (" +
            System.getProperty("os.version") + ", " + System.getProperty("os.arch") + ")";
   }

   private static class Stats {
      private double putsPerSec, getsPerSec;

      public String toString() {
         return "Stats{" +
               "avgPut=" + putsPerSec +
               ", avgGet=" + getsPerSec +
               '}';
      }
   }
}

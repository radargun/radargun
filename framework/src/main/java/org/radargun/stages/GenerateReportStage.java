package org.radargun.stages;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.FilenameFilter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.StringTokenizer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.radargun.logging.Log;
import org.radargun.logging.LogFactory;
import org.radargun.config.Property;
import org.radargun.config.Stage;
import org.radargun.reporting.BarPlotGenerator;
import org.radargun.reporting.BenchmarkResult;
import org.radargun.reporting.ClusterReport;
import org.radargun.reporting.ComparisonChartGenerator;
import org.radargun.reporting.HistogramData;
import org.radargun.reporting.HtmlReportGenerator;
import org.radargun.stressors.SimpleStatistics;
import org.radargun.utils.Utils;

/**
 * Stage that reads in the CSV files produced by CsvReportGenerationStage, generates charts for the aggregated data
 * and writes a HTML report where the charts are presented.
 * <pre>
 * - prefix: the prefix of the generated chart file (png). No value by default, optional parameter.
 * - reportDirectory - where are the csv files located. Defaults to 'reports'
 * - outputDir - where to output the generated graphical reports. Defaults to 'reports'
 * </pre>
 *
 * @author Mircea Markus &lt;Mircea.Markus@jboss.com&gt;
 */
@Stage(internal = true, doc = "Stage that generates a chart from a set of csv files.")
public class GenerateReportStage extends AbstractMasterStage {

   private static Log log = LogFactory.getLog(GenerateReportStage.class);
   private static Pattern resultFilePattern = Pattern.compile("(.*)_(.*)_([0-9][0-9]*).csv");

   public static final String X_LABEL_CLUSTER_SIZE = "Cluster size (number of cache instances)";
   public static final String X_LABEL_ITERATION = "Iteration";
   public static final String REPORTS = "reports";

   @Property(doc = "Path to directory where the report (output) should be generated. Default is '" + GenerateReportStage.REPORTS + "'.")
   private String reportDirectory = REPORTS;

   @Property(doc = "Path to directory where are the (input) CSV files. Default is '" + GenerateReportStage.REPORTS + "'.")
   private String csvFilesDirectory = REPORTS;

   @Property(doc = "Name (prefix) for the report.", name = "name")
   private String prefix;

   private Map<String, List<String>> filter = new HashMap<String, List<String>>();
   private Map<String, List<Pattern>> compiledFilters = null;
   private BenchmarkResult result = new BenchmarkResult();

   public boolean execute() throws Exception {
      File[] files = getFilteredFiles(new File(csvFilesDirectory));
      if (files == null) return true;
      Arrays.sort(files);
      for (File f : files) {
         readData(f);
      }

      int maxIterations = result.getMaxIterations();
      int clusterSizes = result.getClusterSizes().size();
      boolean xLabelClusterSize = clusterSizes > 1 || maxIterations <= 1;

      Map<String, ClusterReport> reports = new HashMap<String, ClusterReport>();
      for (String config : result.getConfigs()) {
         for (int clusterSize : result.getClusterSizes()) {
            for (int iteration: result.getIterations(config, clusterSize)) {

               String categoryName = clusterSizes > 1 && maxIterations > 1 ? config + "_" + iteration : config;
               int x = xLabelClusterSize ? clusterSize : iteration;
               SimpleStatistics aggregated = result.getAggregatedStats(config, clusterSize, iteration);
               for (Map.Entry<String, SimpleStatistics.MeanAndDev> opStats : aggregated.getMeanAndDev(false).entrySet()) {
                  String reportName = opStats.getKey() + "_NET";
                  ClusterReport report = reports.get(reportName);
                  if (report == null) {
                     report = new ClusterReport();
                     reports.put(reportName, report);
                  }
                  report.addCategory(categoryName, x, toMillis(opStats.getValue().mean), toMillis(opStats.getValue().dev));
               }
               for (Map.Entry<String, SimpleStatistics.MeanAndDev> opStats : aggregated.getMeanAndDev(true).entrySet()) {
                  String reportName = opStats.getKey() + "_TX";
                  ClusterReport report = reports.get(reportName);
                  if (report == null) {
                     report = new ClusterReport();
                     reports.put(reportName, report);
                  }
                  report.addCategory(categoryName, x, toMillis(opStats.getValue().mean), toMillis(opStats.getValue().dev));
               }

               for (Map.Entry<String, HistogramData> histogram : result.getHistograms(config, clusterSize, iteration).entrySet()) {
                  histogram.getValue().mergeConstantLogWidth();
                  BarPlotGenerator.generate(histogram.getKey(), histogram.getValue().getRanges(), histogram.getValue().getCounts(),
                        reportDirectory, histogram.getValue().getFileName(prefix), 512, 512);
               }
            }
         }
      }
      for (Map.Entry<String, ClusterReport> entry : reports.entrySet()) {
         String operation = entry.getKey();
         String xLabel = xLabelClusterSize ? X_LABEL_CLUSTER_SIZE : X_LABEL_ITERATION;
         entry.getValue().init(xLabel, "Duration (milliseconds)", null, null);
         ComparisonChartGenerator.generate(entry.getValue(), reportDirectory, prefix + "_" + operation,
               Math.min(Math.max(result.getConfigs().size(), Math.max(clusterSizes, maxIterations)) * 100 + 200, 1800),
               Math.min(result.getConfigs().size() * 100 + 200, 800));
      }

      new HtmlReportGenerator(masterState == null ? null : masterState.getConfig(), result, reportDirectory, prefix).generate();
      return true;
   }

   private void readData(File f) throws IOException {
      log.debug("Processing file " + f);
      //expected file format is: <product>_<config>_<size>.csv
      final String productName;
      final String configName;
      int clusterSize = 0;      
      try {
         StringTokenizer tokenizer = new StringTokenizer(Utils.fileName2Config(f.getName()), "_");
         productName = tokenizer.nextToken();
         configName = tokenizer.nextToken();
         if(tokenizer.hasMoreTokens()) {
            clusterSize = Integer.parseInt(tokenizer.nextToken());
         }
      } catch (Throwable e) {
         String fileName = f == null ? null : f.getAbsolutePath();
         log.error("unexpected exception while parsing filename: " + fileName, e);
         return;
      }

      //now the contents of this file:
      String line;
      BufferedReader br = new BufferedReader(new FileReader(f));
      String header = br.readLine(); //this is the header
      String[] columns = header.split(",", -1);
      //List<OperationColumns> operations = new ArrayList<OperationColumns>();
      List<HistogramColumns> histograms = new ArrayList<HistogramColumns>();
      for (int i = 0; i < columns.length; ++i) {
         String column = columns[i];
         if (column.endsWith("_HISTOGRAM")) {
            String name = column.substring(0, column.length() - 10);
            histograms.add(new HistogramColumns(name, i));
         }
      }
      int slaveIndex = getIndexOf("SLAVE_INDEX", columns);
      int iterIndex = getIndexOf("ITERATION", columns);
      int threadsIndex = getIndexOf("THREADS", columns);

      //Map<String, SimpleStatistics> stats = new HashMap<String, SimpleStatistics>();
      while ((line = br.readLine()) != null) {
         String[] parts = line.split(",", -1);
         if (!parts[slaveIndex].matches(" *[0-9][0-9]* *")) continue;
         int node = Integer.parseInt(parts[slaveIndex].trim());
         SimpleStatistics nodeStats = new SimpleStatistics();
         for (int i = 0; i < parts.length; ++i) {
            int lastDot = columns[i].lastIndexOf('.');
            try {
               nodeStats.parseIn(lastDot < 0 ? columns[i] : columns[i].substring(lastDot + 1), parts[i].trim());
            } catch (Exception e) {
               log.error("Failed to parse in '" + parts[i] + "' from " + f.getName() + " in column " + columns[i], e);
            }
         }
         int iteration = -1;
         if (iterIndex >= 0) {
            try {
               iteration = Integer.parseInt(parts[iterIndex]);
            } catch (NumberFormatException e) {}
         }
         try {
            int threads = Integer.parseInt(parts[threadsIndex].trim());
            result.setThreads(productName,  configName, clusterSize, iteration, node, threads);
         } catch (NumberFormatException e) {}
         result.addNodeStats(productName, configName, clusterSize, iteration, node, nodeStats);
         for (HistogramColumns histogram : histograms) {
            if (parts[histogram.index].isEmpty()) continue;
            try {
               String[] rangesAndCounts = parts[histogram.index].split("=");
               String[] ranges = rangesAndCounts[0].split(":");
               String[] counts = rangesAndCounts[1].split(":");
               //String histogramName;
               boolean tx;
               if (histogram.name.endsWith("_NET")) {
                  //histogramName = histogram.name.substring(0, histogram.name.length() - 4);
                  tx = false;
               } else if (histogram.name.endsWith("_TX")) {
                  //histogramName = histogram.name.substring(0, histogram.name.length() - 3);
                  tx = true;
               } else {
                  throw new IllegalArgumentException(histogram.name);
               }
               result.addHistogramData(productName, configName, clusterSize, iteration, histogram.name, tx, ranges, counts);
            } catch (Exception e) {
               throw new IllegalArgumentException(parts[histogram.index], e);
            }
         }
      }
      br.close();
   }

   private double toMillis(double value) {
      return value / 1000000d;
   }

   private static class HistogramColumns {
      public String name;
      public int index;

      private HistogramColumns(String name, int index) {
         this.name = name;
         this.index = index;
      }
   }

   private int getIndexOf(String string, String[] parts) {
      int i = 0;
      for (String part : parts) {
         if (part.equals(string)) return i;
         ++i;
      }
      return -1;
   }

   public void setPrefix(String prefix) {
      this.prefix = prefix;
   }

   protected File[] getFilteredFiles(File file) {
      return file.listFiles(new FilenameFilter() {
         //accepted file names are <product-name>_<config-name>_<cluster-size>.csv
         public boolean accept(File dir, String name) {
            compileFilters();
            Matcher matcher = resultFilePattern.matcher(name);
            if (!matcher.matches()) return false;
            String productName = matcher.group(1);
            String configName = matcher.group(2);

            if (!isUsingFilters()) {
               return true;
            } else if (!filter.containsKey(productName)) return false;

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
               if (aFilter.equals("*") || aFilter.equals("?") || aFilter.equals("+")) {
                  String oldPatter = aFilter;
                  aFilter = "[" + aFilter + "]";
                  log.info("Converting the pattern from '" + oldPatter + "' to '" + aFilter +"'. " +
                        "See: http://arunma.com/2007/08/23/javautilregexpatternsyntaxexception-dangling-meta-character-near-index-0");
               }
               try {
                  Pattern pattern = Pattern.compile(aFilter);
                  compiled.add(pattern);
               } catch (Exception e) {
                  String message = "Exception while compiling the pattern: '" + aFilter + "'";
                  if (e.getMessage().indexOf("Dangling meta character ") >= 0) {
                     message += "If your regexp is like '*' or '+' (or other methachars), add square brackets to it, " +
                           "e.g. '[*]'. See: http://arunma.com/2007/08/23/javautilregexpatternsyntaxexception-dangling-meta-character-near-index-0/";
                  }
                  log.warn(message, e);
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

   public void setCsvFilesDirectory(String csvFilesDirectory) {
      this.csvFilesDirectory = csvFilesDirectory;
   }

   public void setReportDirectory(String reportDirectory) {
      this.reportDirectory = reportDirectory;
   }
}

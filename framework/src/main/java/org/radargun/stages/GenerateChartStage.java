package org.radargun.stages;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.FilenameFilter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.SortedSet;
import java.util.StringTokenizer;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.radargun.config.Property;
import org.radargun.config.Stage;
import org.radargun.reporting.BarPlotGenerator;
import org.radargun.reporting.ClusterReport;
import org.radargun.reporting.HtmlReportGenerator;
import org.radargun.reporting.LineReportGenerator;
import org.radargun.utils.Table;
import org.radargun.utils.Utils;

/**
 * Stage that generates a chart from a set of csv files.
 * <pre>
 * - fnPrefix: the prefix of the generated chart file (png). No value by default, optional parameter.
 * - reportDirectory - where are the csv files located. Defaults to 'reports'
 * - outputDir - where to output the generated graphical reports. Defaults to 'reports'
 * </pre>
 *
 * @author Mircea Markus &lt;Mircea.Markus@jboss.com&gt;
 */
@Stage(doc = "Stage that generates a chart from a set of csv files.")
public class GenerateChartStage extends AbstractMasterStage {

   private static Log log = LogFactory.getLog(GenerateChartStage.class);
   private static Pattern resultFilePattern = Pattern.compile("(.*)_(.*)_([0-9][0-9]*).csv");

   public static final String X_LABEL_CLUSTER_SIZE = "Cluster size (number of cache instances)";
   public static final String X_LABEL_ITERATION = "Iteration (related to number of stressor threads)";
   public static final String REPORTS = "reports";

   @Property(doc = "Path to directory where the report (output) should be generated. Default is '" + GenerateChartStage.REPORTS + "'.")
   private String reportDirectory = REPORTS;

   @Property(doc = "Path to directory where are the (input) CSV files. Default is '" + GenerateChartStage.REPORTS + "'.")
   private String csvFilesDirectory = REPORTS;

   private String fnPrefix;

   private Map<String, List<String>> filter = new HashMap<String, List<String>>();
   private Map<String, List<Pattern>> compiledFilters = null;
   private Map<String, ClusterReport> reports = new HashMap<String, ClusterReport>();
   private Map<String, HistogramData> histogramReports = new HashMap<String, HistogramData>();

   public boolean execute() throws Exception {
      File[] files = getFilteredFiles(new File(csvFilesDirectory));
      if (files == null) return true;

      boolean xLabelIteration = true;
      for (File f : files) {
         try {
            StringTokenizer tokenizer = new StringTokenizer(Utils.fileName2Config(f.getName()), "_");
            String productName = tokenizer.nextToken();
            String configName = tokenizer.nextToken();
            String prefix = productName + "_" + configName;
            for (File other : files) {
               if (!other.getName().equals(f.getName()) && other.getName().startsWith(prefix)) {
                  xLabelIteration = false;
                  break;
               }
            }
         } catch (Throwable e) {            
         }
      }
      for (File f : files) {
         readData(f, xLabelIteration);
      }
           
      String xLabel = xLabelIteration ? X_LABEL_ITERATION : X_LABEL_CLUSTER_SIZE; 
      for (Map.Entry<String, ClusterReport> entry : reports.entrySet()) {
         String operation = entry.getKey();
         ClusterReport report = entry.getValue();
         entry.getValue().init(xLabel, operation + " ops/sec on each cache instance", "Average " + operation + " per cache instance", getSubtitle());
         LineReportGenerator.generate(entry.getValue(), reportDirectory, fnPrefix + "_" + operation);
         HtmlReportGenerator.generate(entry.getValue(), reportDirectory, fnPrefix + "_" + operation);
      }
      for (Map.Entry<String, HistogramData> histogram : histogramReports.entrySet()) {
         histogram.getValue().mergeConstantLogWidth();
         BarPlotGenerator.generate(histogram.getKey(), histogram.getValue().ranges, histogram.getValue().counts, reportDirectory, fnPrefix + "_" + histogram.getKey() + "_histogram.png");
      }
      return true;
   }

   private void readData(File f, boolean xLabelIteration) throws IOException {
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
      List<OperationColumns> operations = new ArrayList<OperationColumns>();
      List<HistogramColumns> histograms = new ArrayList<HistogramColumns>();
      for (int i = 0; i < columns.length; ++i) {
         String column = columns[i];
         if (column.endsWith("_COUNT")) {
            String name = column.substring(0, column.length() - 6);
            operations.add(new OperationColumns(name, i, getIndexOf(name + "S_PER_SEC", columns)));
         } else if (column.endsWith("_HISTOGRAM")) {
            String name = column.substring(0, column.length() - 10);
            histograms.add(new HistogramColumns(name, i));
         }
      }
      int slaveIndex = getIndexOf("SLAVE_INDEX", columns);
      int iterIndex = getIndexOf("ITERATION", columns);

      Table<String, String, Stats> stats = new Table<String, String, Stats>();
      while ((line = br.readLine()) != null) {
         String[] parts = line.split(",", -1);
         if (!parts[slaveIndex].matches(" *[0-9][0-9]* *")) continue;
         for (OperationColumns operation : operations) {
            double rate = 0;
            int count = 0;
            try {
               rate = parts[operation.rateIndex].isEmpty() ? 0 : Double.parseDouble(parts[operation.rateIndex]);
               count = parts[operation.countIndex].isEmpty() ? 0 : Integer.parseInt(parts[operation.countIndex]);
            } catch (NumberFormatException nfe) {
               log.error("Unable to parse file properly!", nfe);
            }
            String iteration = iterIndex < 0 ? "" : parts[iterIndex];

            Stats s = stats.get(iteration, operation.name);
            if (s == null) {
               s = new Stats();
               stats.put(iteration, operation.name, s);
            }
            s.sumRate += rate;
            s.counts.add(count);
         }
         for (HistogramColumns histogram : histograms) {
            if (parts[histogram.index].isEmpty()) continue;
            try {
               String[] rangesAndCounts = parts[histogram.index].split("=");
               String[] ranges = rangesAndCounts[0].split(":");
               String[] counts = rangesAndCounts[1].split(":");
               HistogramData hist = histogramReports.get(histogram.name);
               if (hist == null) {
                  hist = new HistogramData();
                  histogramReports.put(histogram.name, hist);
               }
               hist.add(ranges, counts);
            } catch (Exception e) {
               throw new IllegalArgumentException(parts[histogram.index], e);
            }
         }
      }
      br.close();
      
      for (String iteration : stats.rowKeys()) {
         String name = productName + "(" + configName + ")" + (xLabelIteration ? "" : iteration);
         int xCoord;
         if (xLabelIteration && !iteration.isEmpty()) {
            try {
               xCoord = Integer.parseInt(iteration);
            } catch (NumberFormatException e) {
               xCoord = -1;
            }
         } else if (xLabelIteration) {
            // if we are using iteration as the x label and there are some non-iteration related data,
            // we'd overwrite the iteration #cluster size
            continue;
         } else {
            xCoord = clusterSize;
         }
         for (String operation : stats.columnKeys()) {
            ClusterReport report = reports.get(operation);
            if (report == null) {
               report = new ClusterReport();
               reports.put(operation, report);
            }
            Stats s = stats.get(iteration, operation);
            if (s == null) {
               log.warn("Null stats for " + operation + " x " + iteration);
               continue;
            }
            int sumOpCount = 0;
            for (int opsCount : s.counts) {
               sumOpCount += opsCount;
            }
            int avgOpCount = sumOpCount / s.counts.size();
            double minDiff = 0, maxDiff = 0;
            for (int opsCount : s.counts) {
               double diff = ((double)(opsCount - avgOpCount))/avgOpCount;
               minDiff = Math.min(minDiff, diff);
               maxDiff = Math.max(maxDiff, diff);
            }
            report.addNote(String.format("%s for %s vary in range [%.2f%%, %.2f%%] from average value", operation, name, minDiff*100, maxDiff*100));

            if (xCoord != -1) {
               report.addCategory(name, xCoord, s.sumRate / clusterSize);
            }
         }
      }
   }

   private static class OperationColumns {
      public String name;
      public int countIndex;
      public int rateIndex;

      private OperationColumns(String name, int countIndex, int rateIndex) {
         this.name = name;
         this.countIndex = countIndex;
         this.rateIndex = rateIndex;
      }
   }

   private static class HistogramColumns {
      public String name;
      public int index;

      private HistogramColumns(String name, int index) {
         this.name = name;
         this.index = index;
      }
   }

   private static class Stats {
      double sumRate = 0;
      List<Integer> counts = new ArrayList<Integer>();
   }

   private static class HistogramData {
      public final static int BUCKETS = 32;

      List<long[]> rangesList = new ArrayList<long[]>();
      List<long[]> countsList = new ArrayList<long[]>();
      private long[] ranges;
      private long[] counts;

      public void mergeConstantHeight() {
         SortedSet<Long> rr = new TreeSet<Long>();
         for (long[] ranges : rangesList) {
            for (int i = 0; i < ranges.length; ++i) {
               rr.add(ranges[i]);
            }
         }
         long[] mergeRanges = new long[rr.size()];
         {
            int i = 0;
            for (Long l : rr) mergeRanges[i++] = l;
         }
         long[] mergeCounts = new long[mergeRanges.length - 1];
         distributeAll(mergeRanges, mergeCounts);
         long[] cumulativeSum = new long[mergeCounts.length];
         long sum = 0;
         for (int i = 0; i < mergeCounts.length; ++i) {
            sum += mergeCounts[i];
            cumulativeSum[i] = sum;
         }
         long[] resultRanges = new long[BUCKETS + 1];
         long[] resultCounts = new long[BUCKETS];
         resultRanges[0] = mergeRanges[0];
         int resultIndex = 0;
         long prevSum = 0;
         for (int i = 0; i < cumulativeSum.length && resultIndex < BUCKETS; ++i) {
            if (cumulativeSum[i] >= (sum * resultIndex)/BUCKETS) {
               resultRanges[resultIndex + 1] = mergeRanges[i + 1];
               resultCounts[resultIndex] = cumulativeSum[i] - prevSum;
               prevSum = cumulativeSum[i];
               resultIndex++;
            }
         }
         this.ranges = resultRanges;
         this.counts = resultCounts;
      }

      private void distributeAll(long[] mergeRanges, long[] mergeCounts) {
         Iterator<long[]> rangesIterator = rangesList.iterator();
         Iterator<long[]> countsIterator = countsList.iterator();
         while (rangesIterator.hasNext()) {
            distribute(mergeRanges, mergeCounts, rangesIterator.next(), countsIterator.next());
         }
      }

      public void mergeConstantLogWidth() {
         long min = Long.MAX_VALUE, max = 0;
         for (long[] ranges : rangesList) {
            min = Math.min(min, ranges[0]);
            max = Math.max(max, ranges[ranges.length - 1]);
         }
         final double base = Math.pow((double) max / (double) min, 1d/BUCKETS);
         ranges = new long[BUCKETS + 1];
         double limit = min;
         for (int i = 0; i <= BUCKETS; ++i) {
            this.ranges[i] = (long) limit;
            limit *= base;
         }
         ranges[BUCKETS] = max;
         counts = new long[BUCKETS];
         distributeAll(ranges, counts);
      }

      private void distribute(long[] resultRanges, long[] resultCounts, long[] myRanges, long[] myCounts) {
         int resultIndex = 1;
         long myPrevLimit = 0;
         long resultPrevLimit = 0;
         while (resultRanges[resultIndex] < myRanges[0]) {
            resultPrevLimit = resultRanges[resultIndex];
            resultIndex++;
         }
         for (int i = 1; i < myRanges.length; ++i) {
            long myInterval = myRanges[i] - myPrevLimit;
            long myCount = myCounts[i - 1];
            while (myInterval > 0 && resultRanges[resultIndex] <= myRanges[i]) {
               long resultInterval = resultRanges[resultIndex] - resultPrevLimit;
               long pushedCount = (resultInterval * myCount) / myInterval;
               resultCounts[resultIndex - 1] += pushedCount;
               myCount -= pushedCount;
               myInterval -= resultInterval;
               resultPrevLimit = resultRanges[resultIndex++];
            }
            if (myCount > 0) {
               resultCounts[resultIndex - 1] += myCount;
            }
            myPrevLimit = myRanges[i];
         }
      }

      public void add(String[] ranges, String[] counts) {
         this.rangesList.add(toLongArray(ranges));
         this.countsList.add(toLongArray(counts));
      }

      private long[] toLongArray(String[] strings) {
         long[] arr = new long[strings.length];
         for (int i = 0; i < strings.length; ++i) {
            arr[i] = Long.parseLong(strings[i]);
         }
         return arr;
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

   public void setFnPrefix(String fnPrefix) {
      this.fnPrefix = fnPrefix;
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

   public static String getSubtitle() {
      return "Generated on " + new Date() + " by RadarGun\nJDK: " +
            System.getProperty("java.vm.name") + " (" + System.getProperty("java.vm.version") + ", " +
            System.getProperty("java.vm.vendor") + ") OS: " + System.getProperty("os.name") + " (" +
            System.getProperty("os.version") + ", " + System.getProperty("os.arch") + ")";
   }

   public void setCsvFilesDirectory(String csvFilesDirectory) {
      this.csvFilesDirectory = csvFilesDirectory;
   }

   public void setReportDirectory(String reportDirectory) {
      this.reportDirectory = reportDirectory;
   }
}

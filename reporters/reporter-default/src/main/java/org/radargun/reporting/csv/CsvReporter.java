package org.radargun.reporting.csv;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.lang.reflect.Type;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;

import org.radargun.config.Cluster;
import org.radargun.config.Converter;
import org.radargun.config.MasterConfig;
import org.radargun.config.Property;
import org.radargun.logging.Log;
import org.radargun.logging.LogFactory;
import org.radargun.reporting.AbstractReporter;
import org.radargun.reporting.Report;
import org.radargun.reporting.Timeline;
import org.radargun.stats.Statistics;
import org.radargun.stats.representation.DataThroughput;
import org.radargun.stats.representation.DefaultOutcome;
import org.radargun.stats.representation.MeanAndDev;
import org.radargun.stats.representation.OperationThroughput;
import org.radargun.stats.representation.Percentile;
import org.radargun.utils.Utils;

/**
 * Reporter producing CSV files.
 *
 * @author Radim Vansa &lt;rvansa@redhat.com&gt;
 */
public class CsvReporter extends AbstractReporter {

   protected static final Log log = LogFactory.getLog(CsvReporter.class);
   protected static final String SLAVE_INDEX = "SlaveIndex";
   protected static final String ITERATION = "Iteration";
   protected static final String PERIOD = "Period";
   protected static final String THREAD_COUNT = "ThreadCount";

   @Property(doc = "Directory into which will be report files written.")
   private String targetDir = "results" + File.separator + "csv";

   @Property(doc = "Slaves whose results will be ignored.")
   private Set<Integer> ignore;

   @Property(doc = "Separator of columns in the CSV file. Default is ','")
   private String separator = ",";

   @Property(doc = "Compute aggregated statistics from all nodes. Default is true.")
   private boolean computeTotal = true;

   @Property(doc = "Compute response times at certain percentiles. Default is 95% and 99%.")
   protected double[] percentiles = new double[] {95d, 99d};

   @Property(doc = "List od comma separated column name regex patterns which should be reordered to the left side, use '\\' to escape the commas if needed", converter = RegexConverter.class)
   protected List<String> columnOrder = Arrays.asList(".*[Put|Get]\\.Throughput", ".*[Put|Get]\\.ResponseTimeMean",
         ".*Get.*");

   @Override
   public void run(MasterConfig masterConfig, Collection<Report> reports) {
      for (Report report : reports) {
         for (Report.Test test : report.getTests()) {
            reportTest(report, test);
         }
         reportTimelines(report);
      }
   }

   private void reportTest(Report report, Report.Test test) {
      FileWriter fileWriter = null;
      try {
         fileWriter = prepareOutputFile(report, test.name, "");
         int it = 0;
         Set<String> columns = new TreeSet<String>();
         ArrayList<Map<String, String>> rows = new ArrayList<>();
         for (Report.TestIteration iteration : test.getIterations()) {
            Statistics aggregated = null;
            for (Map.Entry<Integer, List<Statistics>> slaveStats : iteration.getStatistics()) {
               if (ignore != null && ignore.contains(slaveStats.getKey())) {
                  continue;
               }
               if (slaveStats.getValue().size() <= 0) {
                  continue;
               }
               Statistics nodeSummary = processRow(it, columns, rows, slaveStats);
               if (computeTotal) {
                  if (aggregated == null)
                     aggregated = nodeSummary.copy();
                  else
                     aggregated.merge(nodeSummary);
               }
            }
            if (computeTotal && aggregated != null) {
               Map<String, String> rowData = new HashMap<String, String>();
               rows.add(rowData);
               for (String operation : aggregated.getOperations()) {
                  addRepresentations(aggregated, rowData, aggregated, operation);
               }
               columns.addAll(rowData.keySet());

               rowData.put(SLAVE_INDEX, "TOTAL");
               rowData.put(ITERATION, String.valueOf(it));
               rowData.put(PERIOD, String.valueOf(aggregated.getEnd() - aggregated.getBegin()));
               rowData.put(THREAD_COUNT, String.valueOf(iteration.getThreadCount()));
            }
            ++it;
         }
         writeFile(fileWriter, columns, rows);
      } catch (IOException e) {
         log.error("Failed to create report for test " + test.name, e);
      } finally {
         if (fileWriter != null) {
            try {
               fileWriter.close();
            } catch (IOException e) {
               log.error("Failed to close", e);
            }
         }
      }
   }

   private FileWriter prepareOutputFile(Report report, String prefix, String suffix) throws IOException {
      File parentDir = new File(targetDir);
      if (!parentDir.exists()) {
         if (!parentDir.mkdirs()) {
            log.warn("Issues creating parent dir " + parentDir);
         }
      } else {
         if (!parentDir.isDirectory()) {
            throw new IllegalStateException(targetDir + " is not a directory");
         }
      }

      StringBuilder fileName = new StringBuilder(prefix).append('_').append(report.getConfiguration().name);
      for (Cluster.Group group : report.getCluster().getGroups()) {
         fileName.append('_').append(group.name).append('_').append(group.size);
      }
      fileName.append(suffix).append(".csv");
      File outputFile = Utils.createOrReplaceFile(parentDir, fileName.toString());
      return new FileWriter(outputFile);
   }

   private Statistics processRow(int it, Set<String> columns, List<Map<String, String>> rows,
         Map.Entry<Integer, List<Statistics>> slaveStats) {
      // this reporter is merging statistics from all threads on each node
      Statistics summary = slaveStats.getValue().stream().filter(o -> o != null).reduce(Statistics.MERGE)
         .orElseThrow(() -> new IllegalStateException("No statistics!"));
      Map<String, String> rowData = new HashMap<String, String>();
      rows.add(rowData);
      for (String operation : summary.getOperations()) {
         addRepresentations(summary, rowData, summary, operation);
      }
      columns.addAll(rowData.keySet());

      rowData.put(SLAVE_INDEX, String.valueOf(slaveStats.getKey()));
      rowData.put(ITERATION, String.valueOf(it));
      rowData.put(PERIOD, String.valueOf(summary.getEnd() - summary.getBegin()));
      rowData.put(THREAD_COUNT, String.valueOf(slaveStats.getValue().size()));
      return summary;
   }

   private void addRepresentations(Statistics summary, Map<String, String> rowData, Statistics statistics,
         String operationName) {
      DefaultOutcome defaultOutcome = statistics.getRepresentation(operationName, DefaultOutcome.class);
      if (defaultOutcome != null) {
         if (defaultOutcome.requests == 0) return;
         rowData.put(operationName + ".Requests", String.valueOf(defaultOutcome.requests));
         rowData.put(operationName + ".Errors", String.valueOf(defaultOutcome.errors));
         rowData.put(operationName + ".ResponseTimeMax", String.valueOf(defaultOutcome.responseTimeMax));
         rowData.put(operationName + ".ResponseTimeMean", String.valueOf(round(defaultOutcome.responseTimeMean, 2)));
      }
      OperationThroughput throughput = statistics.getRepresentation(operationName, OperationThroughput.class);
      if (throughput != null) {
         rowData.put(operationName + ".ThroughputWithErrors", String.valueOf(round(throughput.gross, 1)));
         rowData.put(operationName + ".Throughput", String.valueOf(round(throughput.net, 1)));
      }
      for (double percentile : percentiles) {
         Percentile result = statistics.getRepresentation(operationName, Percentile.class, percentile);
         if (result != null) {
            rowData.put(operationName + ".RTM_" + percentile, String.valueOf(round(result.responseTimeMax, 2)));
         }
      }
      MeanAndDev meanAndDev = statistics.getRepresentation(operationName, MeanAndDev.class);
      if (meanAndDev != null) {
         rowData.put(operationName + ".ResponseTimeMean", String.valueOf(round(meanAndDev.mean, 2)));
         rowData.put(operationName + ".ResponseTimeDeviation", String.valueOf(round(meanAndDev.dev, 2)));
      }
      DataThroughput dataThroughput = statistics.getRepresentation(operationName, DataThroughput.class);
      if (dataThroughput != null) {
         rowData.put(operationName + ".DataThrouputMin", String.valueOf(round(dataThroughput.minThroughput, 1)));
         rowData.put(operationName + ".DataThrouputMax", String.valueOf(round(dataThroughput.maxThroughput, 1)));
         rowData.put(operationName + ".DataThrouputMean", String.valueOf(round(dataThroughput.meanThroughput, 1)));
         rowData.put(operationName + ".DataThrouputStdDeviation", String.valueOf(round(dataThroughput.deviation, 2)));
         rowData.put(operationName + ".TotalBytes", String.valueOf(dataThroughput.totalBytes));
         rowData.put(operationName + ".ResponseTimes", Arrays.toString(dataThroughput.responseTimes).replace(",", ""));
      }
   }

   private void writeFile(FileWriter fileWriter, Set<String> columns, List<Map<String, String>> rows)
         throws IOException {

      List<String> indexColumns = Arrays.asList(ITERATION, SLAVE_INDEX, PERIOD, THREAD_COUNT);

      List<String> orderedColumns = new ArrayList<String>(indexColumns);
      orderedColumns.addAll(columns);

      List<String> columnOrderList = new ArrayList<String>(indexColumns);
      columnOrderList.addAll(columnOrder);

      // sorts columns into correct order
      orderedColumns.sort(new ColumnComparator(columnOrderList));

      // sorts rows into correct order
      rows.sort(new RowComparator(Arrays.asList(SLAVE_INDEX, ITERATION, PERIOD, THREAD_COUNT)));

      for (String column : orderedColumns) {
         fileWriter.write(column);
         fileWriter.write(separator);
      }

      fileWriter.write("\n");
      for (Map<String, String> row : rows) {
         for (String column : orderedColumns) {
            String value = row.get(column);
            if (value != null)
               fileWriter.write(value);
            fileWriter.write(separator);
         }
         fileWriter.write("\n");
      }
   }

   private static class ValueAndSlave implements Comparable<ValueAndSlave> {
      Timeline.Value value;
      int slaveIndex;

      private ValueAndSlave(Timeline.Value value, int slaveIndex) {
         this.value = value;
         this.slaveIndex = slaveIndex;
      }

      @Override
      public int compareTo(ValueAndSlave o) {
         int c = Long.compare(value.timestamp, o.value.timestamp);
         return c == 0 ? Integer.compare(slaveIndex, o.slaveIndex) : c;
      }
   }

   private void reportTimelines(Report report) {
      Set<Timeline.Category> allCategories = new HashSet<>();
      int maxSlaveIndex = 0;
      for (Timeline t : report.getTimelines()) {
         allCategories.addAll(t.getValueCategories());
         maxSlaveIndex = Math.max(maxSlaveIndex, t.slaveIndex);
      }
      for (Timeline.Category valueCategory : allCategories) {
         FileWriter writer = null;
         try {
            writer = prepareOutputFile(report, "timeline", "_" + valueCategory.getName());
            writer.write("Timestamp");
            writer.write(separator);
            for (int i = 0; i <= maxSlaveIndex; ++i) {
               writer.write(String.format("Slave %d%s", i, separator));
            }
            writer.write('\n');
            List<ValueAndSlave> values = new ArrayList<ValueAndSlave>();
            for (Timeline t : report.getTimelines()) {
               List<Timeline.Value> list = t.getValues(valueCategory);
               if (list == null)
                  continue;
               for (Timeline.Value v : list) {
                  values.add(new ValueAndSlave(v, t.slaveIndex));
               }
            }
            Collections.sort(values);
            long currTimestamp = Long.MIN_VALUE;
            int nextIndex = -1;
            for (ValueAndSlave vas : values) {
               if (currTimestamp != vas.value.timestamp) {
                  if (nextIndex >= 0) {
                     for (int i = nextIndex; i <= maxSlaveIndex; ++i) {
                        writer.write(separator);
                     }
                     writer.write('\n');
                  }
                  nextIndex = 0;
                  writer.write(String.format("%d%s", vas.value.timestamp, separator));
               }
               for (int i = nextIndex; i < vas.slaveIndex; ++i) {
                  writer.write(separator);
               }
               writer.write(vas.value.value.toString());
               writer.write(separator);
               currTimestamp = vas.value.timestamp;
               nextIndex = vas.slaveIndex + 1;
            }
         } catch (IOException e) {
            log.error("Failed to create timeline report for category " + valueCategory.getName(), e);
         } finally {
            if (writer != null) {
               try {
                  writer.close();
               } catch (IOException e) {
                  log.error("Failed to close", e);
               }
            }
         }
      }
   }

   private double round(double number, int places) {
      BigDecimal bigDecimal = new BigDecimal(number).setScale(places, RoundingMode.HALF_UP);
      return bigDecimal.doubleValue();
   }

   /**
    * Comparator for sorting rows based on column value
    * 
    * @author zhostasa
    *
    */
   private class RowComparator implements Comparator<Map<String, String>> {

      private List<String> comparePriority;

      /**
       * Instantiates comparator
       * 
       * @param comparePriority
       *           List of columns names to sort by, priority is defined by
       *           order in list
       */
      public RowComparator(List<String> comparePriority) {
         this.comparePriority = comparePriority;
      }

      public int compare(Map<String, String> o1, Map<String, String> o2) {
         for (String compareColumn : comparePriority) {
            int comp = o1.get(compareColumn).compareTo(o2.get(compareColumn));
            if (comp != 0)
               return comp;
         }
         return 0;
      }

   }

   /**
    * Comparator for sorting columns by matching regex
    * 
    * @author zhostasa
    *
    */
   private class ColumnComparator implements Comparator<String> {

      private List<String> compareRegex;

      /**
       * Instantiates comparator
       * 
       * @param compareRegex
       *           List of regular expressions to sort by, priority is
       *           determined by order in list
       */
      public ColumnComparator(List<String> compareRegex) {
         this.compareRegex = compareRegex;
      }

      public int compare(String o1, String o2) {

         for (String regex : compareRegex) {
            boolean o1Matches = o1.matches(regex);
            boolean o2Matches = o2.matches(regex);
            if (o1Matches && !o2Matches)
               return -1;
            if (!o1Matches && o2Matches)
               return 1;
         }
         return 0;
      }
   }

   /**
    * A converter for a comma separated list of regular expression arguments,
    * '\\' is used to escape the commas
    * 
    * @author zhostasa
    *
    */
   public static class RegexConverter implements Converter<List<String>> {

      public List<String> convert(String string, Type type) {
         List<String> newList = Arrays.asList(string.split("(?<!\\\\),"));
         return newList;
      }

      public String convertToString(List<String> value) {
         return value.stream().map(n -> n.replace(",", "\\,")).collect(Collectors.joining(","));
      }

      public String allowedPattern(Type type) {
         return ".*";
      }

   }

}

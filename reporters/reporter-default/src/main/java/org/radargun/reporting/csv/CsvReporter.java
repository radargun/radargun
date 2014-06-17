package org.radargun.reporting.csv;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.TimeUnit;

import org.radargun.config.Cluster;
import org.radargun.config.Property;
import org.radargun.config.Scenario;
import org.radargun.logging.Log;
import org.radargun.logging.LogFactory;
import org.radargun.reporting.Report;
import org.radargun.reporting.Reporter;
import org.radargun.reporting.Timeline;
import org.radargun.stats.OperationStats;
import org.radargun.stats.Statistics;
import org.radargun.stats.representation.DefaultOutcome;
import org.radargun.stats.representation.MeanAndDev;
import org.radargun.stats.representation.Throughput;
import org.radargun.utils.Utils;

/**
 * Reporter producing CSV files.
 *
 * @author Radim Vansa &lt;rvansa@redhat.com&gt;
 */
public class CsvReporter implements Reporter {

   protected static final Log log = LogFactory.getLog(CsvReporter.class);
   protected static final String SLAVE_INDEX = "SlaveIndex";
   protected static final String ITERATION = "Iteration";
   protected static final String PERIOD = "Period";
   protected static final String THREAD_COUNT = "ThreadCount";

   @Property(doc = "Directory into which will be report files written.")
   private String targetDir = "results" + File.separator + "csv";

   @Property(doc = "Slaves whose results will be ignored.")
   private Set<Integer> ignore;

   @Property(doc = "Separator of columns in the CSV file. Default is ';'")
   private String separator = ";";

   @Property(doc = "Compute aggregated statistics from all nodes. Default is true.")
   private boolean computeTotal = true;

   @Override
   public void run(Scenario scenario, Collection<Report> reports) {
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
         ArrayList<Map<String, String>> rows = new ArrayList();
         for (Report.TestIteration iteration : test.getIterations()) {
            Statistics aggregated = null;
            int totalThreadCount = 0;
            for (Map.Entry<Integer, List<Statistics>> slaveStats : iteration.statistics.entrySet()) {
               if (ignore != null && ignore.contains(slaveStats.getKey())) {
                  continue;
               }
               if (slaveStats.getValue().size() <= 0) {
                  continue;
               }
               Statistics nodeSummary = processRow(it, columns, rows, slaveStats);
               if (computeTotal) {
                  if (aggregated == null) aggregated = nodeSummary;
                  else aggregated.merge(nodeSummary);
                  totalThreadCount += slaveStats.getValue().size();
               }
            }
            if (computeTotal && aggregated != null) {
               Map<String,OperationStats> operationStats = aggregated.getOperationsStats();
               Map<String, String> rowData = new HashMap<String, String>();
               rows.add(rowData);
               for (Map.Entry<String, OperationStats> os : operationStats.entrySet()) {
                  addRepresentations(aggregated, totalThreadCount, rowData, os.getKey(), os.getValue());
               }
               columns.addAll(rowData.keySet());

               rowData.put(SLAVE_INDEX, "TOTAL");
               rowData.put(ITERATION, String.valueOf(it));
               rowData.put(PERIOD, String.valueOf(aggregated.getEnd() - aggregated.getBegin()));
               rowData.put(THREAD_COUNT, String.valueOf(totalThreadCount));
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

   private Statistics processRow(int it, Set<String> columns, List<Map<String, String>> rows, Map.Entry<Integer, List<Statistics>> slaveStats) {
      // this reporter is merging statistics from all threads on each node
      Statistics summary = null;
      for (Statistics other : slaveStats.getValue()) {
         if (other == null) continue;
         if (summary == null) {
            summary = other.copy();
         } else {
            summary.merge(other);
         }
      }
      Map<String,OperationStats> operationStats = summary.getOperationsStats();
      Map<String, String> rowData = new HashMap<String, String>();
      rows.add(rowData);
      for (Map.Entry<String, OperationStats> os : operationStats.entrySet()) {
         addRepresentations(summary, slaveStats.getValue().size(), rowData, os.getKey(), os.getValue());
      }
      columns.addAll(rowData.keySet());

      rowData.put(SLAVE_INDEX, String.valueOf(slaveStats.getKey()));
      rowData.put(ITERATION, String.valueOf(it));
      rowData.put(PERIOD, String.valueOf(summary.getEnd() - summary.getBegin()));
      rowData.put(THREAD_COUNT, String.valueOf(slaveStats.getValue().size()));
      return summary;
   }

   private void addRepresentations(Statistics summary, int threadCount, Map<String, String> rowData, String operationName, OperationStats os) {
      //Map.Entry<Integer, List<Statistics>> slaveStats = ; Map.Entry<String, OperationStats> os = ;
      DefaultOutcome defaultOutcome = os.getRepresentation(DefaultOutcome.class);
      if (defaultOutcome != null) {
         if (defaultOutcome.requests == 0) return;
         rowData.put(operationName + ".Requests", String.valueOf(defaultOutcome.requests));
         rowData.put(operationName + ".Errors", String.valueOf(defaultOutcome.errors));
         rowData.put(operationName + ".ResponseTimeMax", String.valueOf(defaultOutcome.responseTimeMax));
         rowData.put(operationName + ".ResponseTimeMean", String.valueOf(defaultOutcome.responseTimeMean));
         Throughput throughput = defaultOutcome.toThroughput(threadCount, TimeUnit.MILLISECONDS.toNanos(summary.getEnd() - summary.getBegin()));
         rowData.put(operationName + ".TheoreticalThroughput", String.valueOf(throughput.theoretical));
         rowData.put(operationName + ".ActualThroughput", String.valueOf(throughput.actual));
      }
      MeanAndDev meanAndDev = os.getRepresentation(MeanAndDev.class);
      if (meanAndDev != null) {
         rowData.put(operationName + ".ResponseTimeMean", String.valueOf(meanAndDev.mean));
         rowData.put(operationName + ".ResponseTimeDeviation", String.valueOf(meanAndDev.dev));
      }
   }

   private void writeFile(FileWriter fileWriter, Set<String> columns, List<Map<String, String>> rows) throws IOException {
      List<String> orderedColumns = new ArrayList<String>(Arrays.asList(SLAVE_INDEX, ITERATION, PERIOD, THREAD_COUNT));
      orderedColumns.addAll(columns);
      for (String column : orderedColumns) {
         fileWriter.write(column);
         fileWriter.write(separator);
      }
      fileWriter.write("\n");
      for (Map<String, String> row : rows) {
         for (String column : orderedColumns) {
            String value = row.get(column);
            if (value != null) fileWriter.write(value);
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
      Set<String> allCategories = new HashSet<String>();
      int maxSlaveIndex = 0;
      for (Timeline t : report.getTimelines()) {
         allCategories.addAll(t.getValueCategories());
         maxSlaveIndex = Math.max(maxSlaveIndex, t.slaveIndex);
      }
      for (String valueCategory : allCategories) {
         FileWriter writer = null;
         try {
            writer = prepareOutputFile(report, "timeline", "_" + valueCategory);
            writer.write("Timestamp");
            writer.write(separator);
            for (int i = 0; i <= maxSlaveIndex; ++i) {
               writer.write(String.format("Slave %d%s", i, separator));
            }
            writer.write('\n');
            List<ValueAndSlave> values = new ArrayList<ValueAndSlave>();
            for (Timeline t : report.getTimelines()) {
               List<Timeline.Value> list = t.getValues(valueCategory);
               if (list == null) continue;
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
            log.error("Failed to create timeline report for category " + valueCategory, e);
         } finally {
            if (writer != null) try {
               writer.close();
            } catch (IOException e) {
               log.error("Failed to close", e);
            }
         }
      }
   }

}

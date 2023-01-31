package org.radargun.reporting.commons;

import java.io.File;
import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import org.radargun.config.Cluster;
import org.radargun.logging.Log;
import org.radargun.logging.LogFactory;
import org.radargun.reporting.Report;
import org.radargun.stats.Statistics;
import org.radargun.stats.representation.DataThroughput;
import org.radargun.stats.representation.DefaultOutcome;
import org.radargun.stats.representation.MeanAndDev;
import org.radargun.stats.representation.OperationThroughput;
import org.radargun.stats.representation.Percentile;
import org.radargun.utils.Utils;

public class DataReporter {

   public static final String WORKER_INDEX = "WorkerIndex";
   public static final String ITERATION = "Iteration";
   public static final String PERIOD = "Period";
   public static final String THREAD_COUNT = "ThreadCount";
   private static final Log log = LogFactory.getLog(DataReporter.class);

   private DataReporter() {
   }

   public static DataReportValue get(Report.Test test, Set<Integer> ignore, boolean computeTotal, double[] percentiles) {
      int it = 0;
      Set<String> columns = new TreeSet<String>();
      ArrayList<Map<String, String>> rows = new ArrayList<>();
      long firstTimestamp = Long.MAX_VALUE;
      long lastTimestamp = Long.MIN_VALUE;
      for (Report.TestIteration iteration : test.getIterations()) {
         Statistics aggregated = null;
         for (Map.Entry<Integer, List<Statistics>> workerStats : iteration.getStatistics()) {
            if (ignore != null && ignore.contains(workerStats.getKey())) {
               continue;
            }
            if (workerStats.getValue().size() <= 0) {
               continue;
            }
            Statistics nodeSummary = processRow(it, columns, rows, workerStats, percentiles);
            if (computeTotal) {
               if (aggregated == null)
                  aggregated = nodeSummary.copy();
               else
                  aggregated.merge(nodeSummary);
            }
            firstTimestamp = Math.min(firstTimestamp, nodeSummary.getBegin());
            lastTimestamp = Math.max(lastTimestamp, nodeSummary.getEnd());
         }
         if (computeTotal && aggregated != null) {
            Map<String, String> rowData = new HashMap<String, String>();
            rows.add(rowData);
            for (String operation : aggregated.getOperations()) {
               addRepresentations(aggregated, rowData, aggregated, operation, percentiles);
            }
            columns.addAll(rowData.keySet());

            rowData.put(WORKER_INDEX, "TOTAL");
            rowData.put(ITERATION, String.valueOf(it));
            rowData.put(PERIOD, String.valueOf(aggregated.getEnd() - aggregated.getBegin()));
            rowData.put(THREAD_COUNT, String.valueOf(iteration.getThreadCount()));
         }
         ++it;
      }
      return new DataReportValue(columns, rows, firstTimestamp, lastTimestamp);
   }

   private static Statistics processRow(int it, Set<String> columns, List<Map<String, String>> rows,
                                 Map.Entry<Integer, List<Statistics>> workerStats, double[] percentiles) {
      // this reporter is merging statistics from all threads on each node
      Statistics summary = workerStats.getValue().stream().filter(o -> o != null).reduce(Statistics.MERGE)
            .orElseThrow(() -> new IllegalStateException("No statistics!"));
      Map<String, String> rowData = new HashMap<String, String>();
      rows.add(rowData);
      for (String operation : summary.getOperations()) {
         addRepresentations(summary, rowData, summary, operation, percentiles);
      }
      columns.addAll(rowData.keySet());

      rowData.put(WORKER_INDEX, String.valueOf(workerStats.getKey()));
      rowData.put(ITERATION, String.valueOf(it));
      rowData.put(PERIOD, String.valueOf(summary.getEnd() - summary.getBegin()));
      rowData.put(THREAD_COUNT, String.valueOf(workerStats.getValue().size()));
      return summary;
   }

   private static void addRepresentations(Statistics summary, Map<String, String> rowData, Statistics statistics,
                                   String operationName, double[] percentiles) {
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

   private static double round(double number, int places) {
      BigDecimal bigDecimal = new BigDecimal(number).setScale(places, RoundingMode.HALF_UP);
      return bigDecimal.doubleValue();
   }

   public static File prepareOutputFile(Report report, String prefix, String suffix, String targetDir, String extension) throws IOException {
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
      fileName.append(suffix).append("." + extension);
      File outputFile = Utils.createOrReplaceFile(parentDir, fileName.toString());
      return outputFile;
   }

   public static class DataReportValue {
      public Set<String> columns;
      public List<Map<String, String>> rows;
      public long firstTimestamp;
      public long lastTimestamp;
      public DataReportValue(Set<String> columns, List<Map<String, String>> rows, long firstTimestamp, long lastTimestamp) {
         this.columns = columns;
         this.rows = rows;
         this.firstTimestamp = firstTimestamp;
         this.lastTimestamp = lastTimestamp;
      }
   }
}

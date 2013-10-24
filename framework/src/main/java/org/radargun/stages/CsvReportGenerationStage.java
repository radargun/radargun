package org.radargun.stages;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeSet;

import org.radargun.config.Property;
import org.radargun.config.Stage;
import org.radargun.state.MasterState;
import org.radargun.stressors.SimpleStatistics;
import org.radargun.utils.Utils;

/**
 * Stage that generates reports of StressTest (or TpccBenchmark) in CSV format
 *
 * @author Mircea Markus &lt;Mircea.Markus@jboss.com&gt;
 * @author Radim Vansa &lt;rvansa@redhat.com&gt; (modifications)
 */
@Stage(doc = "Generates report in CSV format from the latest StressTest results")
public class CsvReportGenerationStage extends AbstractMasterStage {

   public static final String RESULTS = "results";

   @Property(doc = "Directory into which will be report files written.")
   private String targetDir = "reports";

   @Property(doc = "Slaves whose results will be ignored.")
   private Set<Integer> ignore;

   @Property(doc = "Adds a line with average results. Default is false.")
   private boolean computeAverage;

   private String separator = ",";

   private File outputFile;
   private FileWriter fileWriter;

   public boolean execute() {
      Map<Integer, Map<String, Object>> results = (Map<Integer, Map<String, Object>>) masterState.get(RESULTS);
      if (results == null) {
         log.error("Could not find reports('results') on the master. Master's state is  " + masterState);
         return false;
      }
      if (ignore != null) {
         for (int slaveIndex : ignore) {
            log.trace("Removing results for slave " + slaveIndex);
            results.remove(slaveIndex);
         }
      }
      try {
         if (results.size() == 0) {
            log.warn("Nothing to report!");
            return false;
         }
         prepareOutputFile(results.size());
         writeData(results);
      } catch (Exception e) {
         log.error("Error writing data", e);
         return false;
      }
      return true;
   }

   private void writeData(Map<Integer, Map<String, Object>> results) throws Exception {

      openFile();

      List<String> headerRow = new ArrayList<String>();
      headerRow.add("SLAVE_INDEX");
      SortedSet<String> iterations = new TreeSet<String>();
      SortedSet<String> columns = new TreeSet<String>();
      for (Map<String, Object> result : results.values()) {
         addIterations(iterations, result.keySet());
         addColumns(columns, result.keySet());
      }
      if (!iterations.isEmpty()) headerRow.add("ITERATION");
      headerRow.addAll(columns);
      writeRowToFile(headerRow);

      List<Integer> slaveIndices = new ArrayList<Integer>(results.keySet());
      Collections.sort(slaveIndices);

      Map<String, SimpleStatistics> agregatedStats = new HashMap<String, SimpleStatistics>();
      Map<String, Integer> threadCounts = new HashMap<String, Integer>();
      for (Integer i : slaveIndices) {
         Map<String, Object> reportPerSlave = results.get(i);
         if (reportPerSlave == null)
            throw new IllegalStateException("Missing report for slave index: " + i);
         writeReport(reportPerSlave, String.valueOf(i), iterations, columns, agregatedStats, threadCounts);
      }
      if (computeAverage) {
         Map<String, Object> average = new HashMap<String, Object>();
         for (Map.Entry<String, SimpleStatistics> entry : agregatedStats.entrySet()) {
            average.putAll(entry.getValue().getResultsMap(threadCounts.get(entry.getKey()), entry.getKey().isEmpty() ? "" : entry.getKey() + "."));
         }
         writeReport(average, "AVG", iterations, columns, null, null);
      }

      closeFile();
   }

   private void writeReport(Map<String, Object> report, String slaveIndex, SortedSet<String> iterations, SortedSet<String> columns,
                            Map<String, SimpleStatistics> aggregated, Map<String, Integer> threadCounts) throws IOException {
      List<String> dataRow = new ArrayList<String>();
      for (String iteration : iterations) {
         dataRow.add(slaveIndex);//add the slave index first
         dataRow.add(iteration);
         if (aggregated != null && !aggregated.containsKey(iteration)) {
            aggregated.put(iteration, new SimpleStatistics());
         }
         addData(report, columns, iteration + '.', dataRow, aggregated == null ? null : aggregated.get(iteration));
         threadCounts.put(iteration, (Integer) report.get(iteration + ".THREADS"));
         writeRowToFile(dataRow);
         dataRow.clear();
      }
      boolean hasDataOutOfIteration = iterations.isEmpty();
      for (String column : columns) {
         if (report.containsKey(column)) {
            hasDataOutOfIteration = true;
            break;
         }
      }
      if (hasDataOutOfIteration) {
         dataRow.add(slaveIndex);//add the slave index first
         if (!iterations.isEmpty()) {
            dataRow.add("");
         }
         if (aggregated != null && !aggregated.containsKey("")) {
            aggregated.put("", new SimpleStatistics());
         }
         if (threadCounts != null) {
            threadCounts.put("", (Integer) report.get("THREADS"));
         }
         addData(report, columns, "", dataRow, aggregated == null ? null : aggregated.get(""));
         writeRowToFile(dataRow);
         dataRow.clear();
      }
   }

   private void addData(Map<String, Object> reportPerSlave, SortedSet<String> columns, String prefix, List<String> dataRow, SimpleStatistics aggregatedStats) {
      SimpleStatistics rowStats = new SimpleStatistics();
      for (String column : columns) {
         String key = prefix + column;
         Object data = reportPerSlave.get(key);
         String dataString = data == null ? "" : String.valueOf(data);
         dataRow.add(dataString);
         if (data != null) {
            rowStats.parseIn(column, dataString);
         }
      }
      if (aggregatedStats != null) {
         aggregatedStats.merge(rowStats);
      }
   }

   private void addColumns(Set<String> columns, Set<String> keySet) {
      for (String key : keySet) {
         int dot = key.indexOf('.');
         if (dot >= 0) {
            columns.add(key.substring(dot + 1));
         } else {
            columns.add(key);
         }
      }
   }

   private void addIterations(Set<String> iterations, Set<String> keySet) {
      for (String key : keySet) {
         int dot = key.indexOf('.');
         if (dot >= 0) {
            iterations.add(key.substring(0, dot));
         }
      }
   }

   private void closeFile() throws IOException {
      fileWriter.close();
   }

   private void openFile() throws IOException {
      fileWriter = new FileWriter(outputFile);
   }

   private void writeRowToFile(List<String> row) throws IOException {
      for (int i = 0; i < row.size(); i++) {
         fileWriter.write(row.get(i));
         if (i == row.size() - 1) {
            fileWriter.write('\n');
         } else {
            fileWriter.write(separator);
         }
      }
   }

   private void prepareOutputFile(int clusterSize) throws IOException {
      File parentDir;
      if (targetDir == null) {
         log.trace("Defaulting to local dir");
         parentDir = new File(".");
      } else {
         parentDir = new File(targetDir);
         if (!parentDir.exists()) {
            if (!parentDir.mkdirs()) {
               log.warn("Issues creating parent dir " + parentDir);
            }
         }
      }
      assert parentDir.exists() && parentDir.isDirectory();

      // file name is in the format data_<cache-product>_<cache-cfg.xml>_<cluster-size>.csv
      String actualFileName =  masterState.nameOfTheCurrentBenchmark() + "_" + masterState.configNameOfTheCurrentBenchmark() + "_" + clusterSize +".csv";

      outputFile = Utils.createOrReplaceFile(parentDir, actualFileName);
   }

   public static void addResult(MasterState masterState, int slaveIndex, String key, Object value) {
      Map<Integer, Map<String, Object>> results = (Map<Integer, Map<String, Object>>) masterState.get(CsvReportGenerationStage.RESULTS);
      if (results == null) {
         results = new HashMap<Integer, Map<String, Object>>();
         masterState.put(CsvReportGenerationStage.RESULTS, results);
      }
      Map<String, Object> slaveResult = results.get(slaveIndex);
      if (slaveResult == null) {
         slaveResult = new HashMap<String, Object>();
         results.put(slaveIndex, slaveResult);
      }
      slaveResult.put(key, value);
   }
}

package org.radargun.stages;

import org.radargun.config.Property;
import org.radargun.config.Stage;
import org.radargun.utils.Utils;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.*;

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
         log.error(e);
         return false;
      }
      return true;
   }

   private void writeData(Map<Integer, Map<String, Object>> results) throws Exception {

      openFile();

      List<String> headerRow = new ArrayList<String>();
      Map<String, Object> aReport = results.get(0);
      headerRow.add("SLAVE_INDEX");
      SortedSet<String> iterations = getIterations(aReport.keySet());
      SortedSet<String> columns = getColumns(aReport.keySet());
      if (!iterations.isEmpty()) headerRow.add("ITERATION");
      headerRow.addAll(columns);
      writeRowToFile(headerRow);

      List<Integer> slaveIndexes = new ArrayList<Integer>(results.keySet());
      Collections.sort(slaveIndexes);

      List<String> dataRow = new ArrayList<String>();
      for (Integer i : slaveIndexes) {
         Map<String, Object> reportPerSlave = results.get(i);
         if (reportPerSlave == null)
            throw new IllegalStateException("Missing report for slave index: " + i);
         for (String iteration : iterations) {
            dataRow.add(String.valueOf(i));//add the slave index first
            dataRow.add(iteration);               
            addData(reportPerSlave, columns, iteration + '.', dataRow);
            writeRowToFile(dataRow);
            dataRow.clear();
         }
         boolean hasDataOutOfIteration = iterations.isEmpty();
         for (String column : columns) {
            if (reportPerSlave.containsKey(column)) {
               hasDataOutOfIteration = true;
               break;
            }
         }
         if (hasDataOutOfIteration) {
            dataRow.add(String.valueOf(i));//add the slave index first
            if (!iterations.isEmpty()) {
               dataRow.add("");
            }
            addData(reportPerSlave, columns, "", dataRow);
            writeRowToFile(dataRow);
            dataRow.clear();
         }
      }

      closeFile();
   }

   private void addData(Map<String, Object> reportPerSlave, SortedSet<String> columns, String prefix, List<String> dataRow) {
      for (String column : columns) {
         Object data = reportPerSlave.get(prefix + column);
         //if (data == null)
         //   throw new IllegalStateException("Missing data for header: " + header + " from slave " + i + ". Report for this slave is: " + reportPerSlave);
         dataRow.add(data == null ? "" : String.valueOf(data));
      }
   }

   private SortedSet<String> getColumns(Set<String> keySet) {
      SortedSet<String> list = new TreeSet<String>();
      for (String key : keySet) {
         int dot = key.indexOf('.');
         if (dot >= 0) {
            list.add(key.substring(dot + 1));
         } else {
            list.add(key);
         }
      }
      return list;
   }

   private SortedSet<String> getIterations(Set<String> keySet) {
      SortedSet<String> iterations = new TreeSet<String>();
      for (String key : keySet) {
         int dot = key.indexOf('.');
         if (dot >= 0) {
            iterations.add(key.substring(0, dot));
         }
      }
      return iterations;
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
}

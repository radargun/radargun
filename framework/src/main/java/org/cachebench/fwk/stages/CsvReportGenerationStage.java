package org.cachebench.fwk.stages;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * // TODO: Mircea - Document this!
 *
 * @author Mircea.Markus@jboss.com
 */
public class CsvReportGenerationStage extends AbstractServerStage {

   private static Log log = LogFactory.getLog(CsvReportGenerationStage.class);

   private String fileNamePrefix = "benchmark-result";
   private String targetDir = ".";
   private String sepparator = ";";

   private File outputFile;
   private FileWriter fileWriter;

   public boolean execute() {
      Map<Integer, Map<String, Object>> results = (Map<Integer, Map<String, Object>>) serverState.get("results");
      if (results == null) {
         log.error("Could not find reports('results') on the server. Server state is  " + serverState);
         return false;
      }
      try {
         prepareOutputFile();
         if (results.size() == 0) {
            log.warn("Nothing to report!");
            return false;
         }
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
      headerRow.add("NODE_INDEX");
      headerRow.addAll(aReport.keySet());
      writeRowToFile(headerRow);

      List<String> dataRow = new ArrayList<String>();
      for (int i = 0; i < serverState.getConfig().getNodeCount(); i++) {
         Map<String, Object> reportPerNode = results.get(i);
         if (reportPerNode == null)
            throw new IllegalStateException("Missing report for node index: " + i);
         dataRow.add(String.valueOf(i));//add the node index first
         for (int j = 1; j < headerRow.size(); j++) {
            String header = headerRow.get(j);
            Object data = reportPerNode.get(header);
            if (data == null)
               throw new IllegalStateException("Missing data for header: " + header + " from node " + i + ". Report for this node is: " + reportPerNode);
            dataRow.add(String.valueOf(data));
         }
         writeRowToFile(dataRow);
         dataRow.clear();
      }

      closeFile();
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
            fileWriter.write(sepparator);
         }
      }
   }

   private void prepareOutputFile() throws IOException {
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

      String actualFileName = fileNamePrefix + "-" + serverState.getConfig().getNodeCount() + ".csv";

      outputFile = new File(parentDir, actualFileName);

      if (outputFile.exists()) {
         String fileName = outputFile.getAbsolutePath() + ".old." + System.currentTimeMillis();
         log.info("A file named: '" + outputFile.getAbsolutePath() + "' already exist. Renaming to '" + fileName + "'");
         if (!outputFile.renameTo(new File(fileName))) {
            log.warn("Could not rename!!!");
         }
      } else {
         if (outputFile.createNewFile()) {
            log.info("Successfully created report file:" + outputFile.getAbsolutePath());
         } else {
            log.warn("Failed to create the report file!");
         }
      }
   }

   public void setFileNamePrefix(String fileNamePrefix) {
      this.fileNamePrefix = fileNamePrefix;
   }

   public void setTargetDir(String targetDir) {
      this.targetDir = targetDir;
   }
}

package org.cachebench.reportgenerators.reportcentralizer;

import java.io.*;
import java.util.*;

/**
 * @author Mircea.Markus@jboss.com
 */
public class ReportCentralizer {

   public static final String SEPARATOR = ", ";

   private String sourceReportDirectory;

   private String destinationReportDirectory;


   public ReportCentralizer(String reportsSourceDir, String reportsDestDir) throws Exception {
      this.sourceReportDirectory = reportsSourceDir;
      this.destinationReportDirectory = reportsDestDir;
      process();
   }

   private void process() throws Exception {
      File[] files = getReportFiles();
      List<ReportData> reportDatas = parseFiles(files);
      List<ConfigurationData> configurationDatas = buildBenchmarkData(reportDatas);
      updateConfigurationDatas(reportDatas, configurationDatas);
      generateReports(configurationDatas);
   }

   private void generateReports(List<ConfigurationData> configurationDatas) throws IOException {
      File destDir = new File(destinationReportDirectory);
      if (!destDir.exists()) {
         destDir.mkdir();
      }
      for (ConfigurationData configData : configurationDatas) {
         File reportFile = new File(destDir, configData.getConfigurationName() + ".csv");
         reportFile.createNewFile();
         PrintStream printStream = new PrintStream(reportFile);
         writeHeader(printStream, configData.getClusterSizes());
         Map<String,List<ReportData>> map = configData.getDistriution2ReportDataMap();
         for (Map.Entry<String,List<ReportData>> entry : map.entrySet())
         {
            printProduct(printStream, entry);
         }
         printStream.flush();
         printStream.close();
      }
   }

   private void printProduct(PrintStream printStream, Map.Entry<String, List<ReportData>> entry) {
      printStream.print(entry.getKey());
      for (ReportData reportData: entry.getValue())
      {
         printStream.print(SEPARATOR + reportData.getAvgReqPerSec());
      }
      printStream.println();
   }

   private void writeHeader(PrintStream printStream, int[] clusterSizes) {
      printStream.print("Cluster Size");
      for (int i = 0; i < clusterSizes.length; i++) {
         printStream.print(SEPARATOR + i);
      }
      printStream.println();
   }

   private void updateConfigurationDatas(List<ReportData> reportDatas, List<ConfigurationData> configurationDatas) {
      for (ReportData data : reportDatas) {
         for (ConfigurationData configurationData : configurationDatas) {
            configurationData.addIfNeeded(data);
         }
      }
   }

   private List<ConfigurationData> buildBenchmarkData(List<ReportData> reportDatas) {
      Set<String> alreadyProcessed = new HashSet<String>();
      List<ConfigurationData> result = new ArrayList<ConfigurationData>();
      for (ReportData reportData : reportDatas) {
         if (!alreadyProcessed.contains(reportData.getConfiguration())) {
            alreadyProcessed.add(reportData.getConfiguration());
            result.add(new ConfigurationData(reportData.getConfiguration()));
         }
      }
      return result;
   }

   private ArrayList<ReportData> parseFiles(File[] files) throws Exception {
      ArrayList<ReportData> reportDatas = new ArrayList<ReportData>();
      for (File file : files) {
         reportDatas.add(new ReportData(file));
      }
      return reportDatas;
   }

   private File[] getReportFiles() {
      File file = new File(sourceReportDirectory);
      if (!file.exists() || !file.isDirectory())
         throw new IllegalArgumentException("Report directory " + sourceReportDirectory + " does not exist or is not a directory!");
      return file.listFiles(new FilenameFilter() {
         public boolean accept(File dir, String name) {
            return name.toUpperCase().endsWith(".CSV");
         }
      });
   }

   public static void main(String[] args) throws Exception {
      String sourceDir = "c:\\temp\\centralizer\\in_real2";
      String destDir = "c:\\temp\\centralizer\\in_real2\\centralized";
      new ReportCentralizer(sourceDir, destDir);
   }
}

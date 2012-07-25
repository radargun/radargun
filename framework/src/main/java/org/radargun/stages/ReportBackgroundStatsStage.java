package org.radargun.stages;

import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.text.DecimalFormat;
import java.text.Format;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.radargun.reporting.CSVChart;
import org.radargun.stressors.BackgroundStats;
import org.radargun.stressors.BackgroundStats.Stats;

/**
 * 
 * Generates reports from BackgroundStats results.
 * 
 * @author Michal Linhard <mlinhard@redhat.com>
 * 
 */
public class ReportBackgroundStatsStage extends AbstractMasterStage {
   public static final Format NUMFORMAT = new DecimalFormat("0.000");

   private String targetDir = "reports";
   private int chartWidth = 800;
   private int chartHeight = 600;

   public boolean execute() {
      @SuppressWarnings("unchecked")
      List<List<Stats>> results = (List<List<Stats>>) masterState.get(BackgroundStats.NAME);
      if (results == null) {
         log.error("Could not find BackgroundStats results on the master. Master's state is  " + masterState);
         return false;
      }
      if (results.size() == 0) {
         log.warn("Nothing to report!");
         return false;
      }
      if (masterState.getSlavesCountForCurrentStage() != results.size()) {
         log.error("We're missing statistics from some slaves");
         return false;
      }
      try {
         File reportDir = new File(targetDir);
         if (!reportDir.exists() && !reportDir.mkdirs()) {
            log.error("Couldn't create directory " + targetDir);
            return false;
         }
         File subdir = new File(reportDir, masterState.nameOfTheCurrentBenchmark() + "_"
               + masterState.configNameOfTheCurrentBenchmark() + "_" + results.size());
         if (!subdir.exists() && !subdir.mkdirs()) {
            log.error("Couldn't create directory " + subdir.getAbsolutePath());
            return false;
         }
         int maxResultSize = -1;
         for (int i = 0; i < results.size(); i++) {
            if (maxResultSize < results.get(i).size()) {
               maxResultSize = results.get(i).size();
            }
         }

         File csvThroughput = new File(subdir, "throughput.csv");
         File csvAvgRespTimes = new File(subdir, "avg-response-times.csv");
         File csvEntryCounts = new File(subdir, "entry-counts.csv");
         File csvNullGets = new File(subdir, "null-responses.csv");
         File csvErrors = new File(subdir, "errors.csv");

         generateMultiSlaveCsv(csvThroughput, results, maxResultSize, new StatGetter() {
            @Override
            public String getStat(Stats cell) {
               if (cell.isNodeUp()) {
                  return ffcsv(cell.getThroughput());
               } else {
                  return "0.0";
               }
            }
         });
         generateMultiSlaveCsv(csvAvgRespTimes, results, maxResultSize, new StatGetter() {
            @Override
            public String getStat(Stats cell) {
               if (cell.isNodeUp()) {
                  return ffcsv(cell.getAvgResponseTime());
               } else {
                  return "0.0";
               }
            }
         });
         generateMultiSlaveCsv(csvNullGets, results, maxResultSize, new StatGetter() {
            @Override
            public String getStat(Stats cell) {
               if (cell.isNodeUp()) {
                  return Long.toString(cell.getRequestsNullGet());
               } else {
                  return "0";
               }
            }
         });
         generateMultiSlaveCsv(csvErrors, results, maxResultSize, new StatGetter() {
            @Override
            public String getStat(Stats cell) {
               if (cell.isNodeUp()) {
                  return Long.toString(cell.getNumErrors());
               } else {
                  return "0";
               }
            }
         });
         generateEntryCountCsv(csvEntryCounts, results, maxResultSize);

         CSVChart.writeCSVAsChart("Throughput on slaves", "Iteration", "Throughput (ops/sec)",
               csvThroughput.getAbsolutePath(), CSVChart.SEPARATOR, "Iteration", getSlaveNames(), chartWidth,
               chartHeight, replaceExtension(csvThroughput.getAbsolutePath(), "png"));
         CSVChart.writeCSVAsChart("Average response times", "Iteration", "Average response time (ms)",
               csvAvgRespTimes.getAbsolutePath(), CSVChart.SEPARATOR, "Iteration", getSlaveNames(), chartWidth,
               chartHeight, replaceExtension(csvAvgRespTimes.getAbsolutePath(), "png"));
         CSVChart.writeCSVAsChart("Entry counts in slaves", "Iteration", "Number of entries",
               csvEntryCounts.getAbsolutePath(), CSVChart.SEPARATOR, "Iteration", getSlaveNames(), chartWidth,
               chartHeight, replaceExtension(csvEntryCounts.getAbsolutePath(), "png"));
         CSVChart.writeCSVAsChart("Max. Relative deviation of entry counts", "Iteration", "Relative deviation (%)",
               csvEntryCounts.getAbsolutePath(), CSVChart.SEPARATOR, "Iteration",
               Collections.singletonList("MaxRelDev"), chartWidth, chartHeight,
               replaceExtension(csvEntryCounts.getAbsolutePath(), "deviation.png"));
         CSVChart.writeCSVAsChart("Null response count", "Iteration", "Number of null responses",
               csvNullGets.getAbsolutePath(), CSVChart.SEPARATOR, "Iteration", getSlaveNames(), chartWidth,
               chartHeight, replaceExtension(csvNullGets.getAbsolutePath(), "png"));
         CSVChart.writeCSVAsChart("Number of errors on slaves", "Iteration", "Number of errors",
               csvErrors.getAbsolutePath(), CSVChart.SEPARATOR, "Iteration", getSlaveNames(), chartWidth, chartHeight,
               replaceExtension(csvErrors.getAbsolutePath(), "png"));

         return true;
      } catch (Exception e) {
         log.error("Error while generating CSV from BackgroundStats", e);
         return false;
      }
   }

   private String replaceExtension(String filename, String newExtension) {
      if (filename == null) {
         return null;
      }
      int dotIndex = filename.lastIndexOf(".");
      if (dotIndex == -1) {
         return filename + newExtension;
      } else {
         return filename.substring(0, dotIndex + 1) + newExtension;
      }
   }

   private static String ffcsv(double val) {
      return (Double.isNaN(val) || val == Double.MAX_VALUE || val == Double.MIN_VALUE) ? CSVChart.NULL : NUMFORMAT
            .format(val);
   }

   private List<String> getSlaveNames() {
      List<String> result = new ArrayList<String>();
      for (int i = 0; i < masterState.getSlavesCountForCurrentStage(); i++) {
         result.add("slave" + i);
      }
      return result;
   }

   private void generateMultiSlaveCsv(File file, List<List<Stats>> results, int maxResultSize, StatGetter getter)
         throws Exception {
      PrintWriter w = new PrintWriter(new OutputStreamWriter(new FileOutputStream(file), "UTF-8"));
      w.print("Iteration");
      List<String> slaveNames = getSlaveNames();
      for (int i = 0; i < slaveNames.size(); i++) {
         w.print(CSVChart.SEPARATOR);
         w.print(slaveNames.get(i));
      }
      w.println();
      for (int i = 0; i < maxResultSize; i++) {
         w.print(i);
         for (int j = 0; j < results.size(); j++) {
            w.print(CSVChart.SEPARATOR);
            List<Stats> statList = results.get(j);
            if (i < statList.size()) {
               w.print(getter.getStat(statList.get(i)));
            } else {
               w.print(CSVChart.NULL);
            }
         }
         w.println();
      }
      w.close();
   }

   private void generateEntryCountCsv(File file, List<List<Stats>> results, int maxResultSize) throws Exception {
      PrintWriter w = new PrintWriter(new OutputStreamWriter(new FileOutputStream(file), "UTF-8"));
      w.print("Iteration");
      List<String> slaveNames = getSlaveNames();
      for (int i = 0; i < slaveNames.size(); i++) {
         w.print(CSVChart.SEPARATOR);
         w.print(slaveNames.get(i));
      }
      w.print(CSVChart.SEPARATOR);
      w.print("MaxRelDev");
      w.println();
      List<Stats> row = new ArrayList<Stats>();
      for (int i = 0; i < maxResultSize; i++) {
         w.print(i);
         row.clear();
         for (int j = 0; j < results.size(); j++) {
            w.print(CSVChart.SEPARATOR);
            List<Stats> statList = results.get(j);
            if (i < statList.size() && statList.get(i).getCacheSize() != -1 && statList.get(i).isNodeUp()) {
               w.print(statList.get(i).getCacheSize());
               row.add(statList.get(i));
            } else {
               w.print(0);
            }
         }
         w.print(CSVChart.SEPARATOR);
         w.print(ffcsv(Stats.getCacheSizeMaxRelativeDeviation(row)));
         w.println();
      }
      w.close();
   }

   private interface StatGetter {
      String getStat(Stats cell);
   }

   public void setTargetDir(String targetDir) {
      this.targetDir = targetDir;
   }

}

package org.radargun.stages;

import java.io.*;
import java.text.DecimalFormat;
import java.text.Format;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.radargun.config.Property;
import org.radargun.config.Stage;
import org.radargun.reporting.CSVChart;
import org.radargun.stages.cache.background.BackgroundOpsManager;
import org.radargun.stats.SimpleStatistics;

/**
 * Generates reports from Statistics.
 * 
 * @author Michal Linhard <mlinhard@redhat.com>
 */
@Stage(doc = "Generates reports from Statistics.")
@Deprecated
public class ReportBackgroundStatsStage extends AbstractMasterStage {
   public static final Format NUMFORMAT = new DecimalFormat("0.000");
   private static final SimpleDateFormat DATEFORMAT = new SimpleDateFormat("HH:mm:ss,SSS");
   private static final double NS_IN_MS = 1000000;

   @Property(doc = "Directory where the reports should be written. Default is 'reports'")
   private String targetDir = "reports";

   @Property(doc = "Width of the produced charts. Default is 800px.")
   private int chartWidth = 800;

   @Property(doc = "Height of the produced charts. Default is 600px.")
   private int chartHeight = 600;

   @Property(doc = "Generate files for verifying time synchronization of slaves. Default is false.")
   private boolean generateIntervalTimeData = false;

   @Property(doc = "Set of slaves whose results should be ignored. Default is empty.")
   private Set<Integer> ignore;

   public boolean execute() {
      @SuppressWarnings("unchecked")
      Map<Integer, List<SimpleStatistics>> allResults = (Map<Integer, List<SimpleStatistics>>) masterState.get(BackgroundOpsManager.NAME);
      if (allResults == null) {
         log.error("Could not find BackgroundStressors results on the master. Master's state is  " + masterState);
         return false;
      }
      if (allResults.size() == 0) {
         log.warn("Nothing to report!");
         return false;
      }
      if (masterState.getClusterSize() != allResults.size()) {
         log.error("We're missing statistics from some slaves");
         return false;
      }
      
      List<List<SimpleStatistics>> results = new ArrayList<List<SimpleStatistics>>();
      if (ignore != null) {
         for (int slave : allResults.keySet()) {
            if (!ignore.contains(slave)) {
               results.add(allResults.get(slave));
            }
         }
      } else {
         results.addAll(allResults.values());
      }
      
      try {
         File reportDir = new File(targetDir);
         if (!reportDir.exists() && !reportDir.mkdirs()) {
            log.error("Couldn't create directory " + targetDir);
            return false;
         }
         File subdir = new File(reportDir, masterState.getConfigName() + "_" + results.size());
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
         File csvTotalThroughput = new File(subdir, "total-throughput.csv");
         File csvAvgRespTimes = new File(subdir, "avg-response-times.csv");
         File csvEntryCounts = new File(subdir, "entry-counts.csv");
         File csvNullGets = new File(subdir, "null-responses.csv");
         File csvErrors = new File(subdir, "errors.csv");
         File csvTotals = new File(subdir, "total.csv");

         generateMultiSlaveCsv(csvThroughput, results, maxResultSize, new StatGetter() {
            @Override
            public String getStat(SimpleStatistics cell) {
               if (cell != null && cell.isNodeUp()) {
                  return ffcsv(cell.getThroughput());
               } else {
                  return "0.0";
               }
            }
         });
         generateMultiSlaveCsv(csvAvgRespTimes, results, maxResultSize, new StatGetter() {
            @Override
            public String getStat(SimpleStatistics cell) {
               if (cell != null && cell.isNodeUp()) {
                  return ffcsv(cell.getAvgResponseTime() / NS_IN_MS);
               } else {
                  return "0.0";
               }
            }
         });
         generateMultiSlaveCsv(csvNullGets, results, maxResultSize, new StatGetter() {
            @Override
            public String getStat(SimpleStatistics cell) {
               if (cell != null && cell.isNodeUp()) {
                  return Long.toString(cell.getRequestsNullGet());
               } else {
                  return "0";
               }
            }
         });
         generateMultiSlaveCsv(csvErrors, results, maxResultSize, new StatGetter() {
            @Override
            public String getStat(SimpleStatistics cell) {
               if (cell != null && cell.isNodeUp()) {
                  return Long.toString(cell.getNumErrors());
               } else {
                  return "0";
               }
            }
         });
         if (generateIntervalTimeData) {
            generateMultiSlaveCsv(new File(subdir, "interval-time-begin.csv"), results, maxResultSize,
                  new StatGetter() {
                     @Override
                     public String getStat(SimpleStatistics cell) {
                        return cell != null ? humanReadableTime(cell.getIntervalBeginTime()) : "";
                     }
                  });
            generateMultiSlaveCsv(new File(subdir, "interval-time-end.csv"), results, maxResultSize, new StatGetter() {
               @Override
               public String getStat(SimpleStatistics cell) {
                  return cell != null ? humanReadableTime(cell.getIntervalEndTime()) : "";
               }
            });
            generateMultiSlaveCsv(new File(subdir, "interval-duration.csv"), results, maxResultSize, new StatGetter() {
               @Override
               public String getStat(SimpleStatistics cell) {
                  return cell != null ? Long.toString(cell.getDuration()) : "";
               }
            });
         }

         generateEntryCountCsv(csvEntryCounts, results, maxResultSize);
         generateTotalsCsv(csvTotals, results, maxResultSize);
         
         
         generateTotalThroughputCsvFromThroughputCsv(csvThroughput.getAbsolutePath(), csvTotalThroughput.getAbsolutePath());
         
         List<String> total = new ArrayList<String>();
         total.add("TOTAL");

         CSVChart.writeCSVAsChart("Throughput on slaves", "Iteration", "Throughput (ops/sec)",
               csvThroughput.getAbsolutePath(), CSVChart.SEPARATOR, "Iteration", getSlaveNames(), chartWidth,
               chartHeight, replaceExtension(csvThroughput.getAbsolutePath(), "png"));
         
         CSVChart.writeCSVAsChart("Total Throughput", "Iteration", "Throughput (ops/sec)",
                 csvTotalThroughput.getAbsolutePath(), CSVChart.SEPARATOR, "Iteration", total, chartWidth,
                 chartHeight, replaceExtension(csvTotalThroughput.getAbsolutePath(), "png"));         
         
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
         log.error("Error while generating CSV from BackgroundStressors", e);
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
      for (int i = 0; i < masterState.getClusterSize(); i++) {
         result.add("slave" + i);
      }
      return result;
   }

   private void generateMultiSlaveCsv(File file, List<List<SimpleStatistics>> results, int maxResultSize, StatGetter getter)
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
            List<SimpleStatistics> statList = results.get(j);
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

   private void generateEntryCountCsv(File file, List<List<SimpleStatistics>> results, int maxResultSize) throws Exception {
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
      List<SimpleStatistics> row = new ArrayList<SimpleStatistics>();
      for (int i = 0; i < maxResultSize; i++) {
         w.print(i);
         row.clear();
         for (int j = 0; j < results.size(); j++) {
            w.print(CSVChart.SEPARATOR);
            List<SimpleStatistics> statList = results.get(j);
            if (i < statList.size() && statList.get(i).getCacheSize() != -1 && statList.get(i).isNodeUp()) {
               w.print(statList.get(i).getCacheSize());
               row.add(statList.get(i));
            } else {
               w.print(0);
            }
         }
         w.print(CSVChart.SEPARATOR);
         w.print(ffcsv(SimpleStatistics.getCacheSizeMaxRelativeDeviation(row)));
         w.println();
      }
      w.close();
   }

   private void generateTotalsCsv(File file, List<List<SimpleStatistics>> results, int maxResultSize) throws Exception {
      PrintWriter w = new PrintWriter(new OutputStreamWriter(new FileOutputStream(file), "UTF-8"));
      w.print("Iteration");
      w.print(CSVChart.SEPARATOR);
      w.print("IntervalBeginMin");
      w.print(CSVChart.SEPARATOR);
      w.print("IntervalEndMax");
      w.print(CSVChart.SEPARATOR);
      w.print("IntervalBeginDiff");
      w.print(CSVChart.SEPARATOR);
      w.print("IntervalEndDiff");

      w.print(CSVChart.SEPARATOR);
      w.print("TotalThroughput");
      w.print(CSVChart.SEPARATOR);
      w.print("AvgThroughput");
      w.print(CSVChart.SEPARATOR);
      w.print("AvgRespTime");
      w.print(CSVChart.SEPARATOR);
      w.print("MaxRespTime");
      w.print(CSVChart.SEPARATOR);
      w.print("EntryCount");
      w.print(CSVChart.SEPARATOR);
      w.print("Errors");
      w.print(CSVChart.SEPARATOR);
      w.print("NullRequests");
      w.print(CSVChart.SEPARATOR);
      w.print("SlaveCount");
      w.println();
      List<SimpleStatistics> row = new ArrayList<SimpleStatistics>();
      List<SimpleStatistics> rowAll = new ArrayList<SimpleStatistics>();
      for (int i = 0; i < maxResultSize; i++) {
         row.clear();
         rowAll.clear();
         for (int j = 0; j < results.size(); j++) {
            List<SimpleStatistics> statList = results.get(j);
            if (i < statList.size()) {
               if (statList.get(i).isNodeUp()) {
                  row.add(statList.get(i));
               }
               rowAll.add(statList.get(i));
            }
         }
         w.print(i);

         long beginMin = SimpleStatistics.getIntervalBeginMin(rowAll);
         long beginMax = SimpleStatistics.getIntervalBeginMax(rowAll);
         long endMin = SimpleStatistics.getIntervalEndMin(rowAll);
         long endMax = SimpleStatistics.getIntervalEndMax(rowAll);

         w.print(CSVChart.SEPARATOR);
         w.print(humanReadableTime(beginMin));
         w.print(CSVChart.SEPARATOR);
         w.print(humanReadableTime(endMax));
         w.print(CSVChart.SEPARATOR);
         w.print(beginMax - beginMin);
         w.print(CSVChart.SEPARATOR);
         w.print(endMax - endMin);
         w.print(CSVChart.SEPARATOR);
         w.print(SimpleStatistics.getTotalThroughput(row));
         w.print(CSVChart.SEPARATOR);
         w.print(SimpleStatistics.getAvgThroughput(row));
         w.print(CSVChart.SEPARATOR);
         w.print(ffcsv(SimpleStatistics.getAvgRespTime(row)));
         w.print(CSVChart.SEPARATOR);
         w.print(SimpleStatistics.getMaxRespTime(row));
         w.print(CSVChart.SEPARATOR);
         w.print(SimpleStatistics.getTotalCacheSize(row));
         w.print(CSVChart.SEPARATOR);
         w.print(SimpleStatistics.getTotalErrors(row));
         w.print(CSVChart.SEPARATOR);
         w.print(SimpleStatistics.getTotalNullRequests(row));
         w.print(CSVChart.SEPARATOR);
         w.print(row.size());
         w.println();
      }
      w.close();
   }

   private String humanReadableTime(long aTime) {
      return DATEFORMAT.format(new Date(aTime));
   }

   private interface StatGetter {
      String getStat(SimpleStatistics cell);
   }

   private void generateTotalThroughputCsvFromThroughputCsv(String from, String to){

	   try{
			// Create output file 
			FileWriter fstreamOut = new FileWriter(to);
			BufferedWriter out = new BufferedWriter(fstreamOut);
			// Initialize input 
			FileInputStream fstreamIn = new FileInputStream(from);
			DataInputStream in = new DataInputStream(fstreamIn);
			BufferedReader br = new BufferedReader(new InputStreamReader(in));
			// initialize strLine
			String strLine;
			
			//first line
			br.readLine();
			out.write("Iteration;TOTAL" + System.getProperty( "line.separator" ));
			
			String[] temp;
			String delimiter=";"; 
			double toReturn;
			while ((strLine = br.readLine()) != null)   {
				toReturn=0;
				temp = strLine.split(delimiter);
				for (int i=1; i<temp.length; i++){
					toReturn = toReturn + addValue(temp[i]); 
				}
				
				out.write(temp[0] +";"+toReturn);
				out.write(System.getProperty( "line.separator" ));
			}
			
			//Close the input and output stream
			in.close();
			out.close();
		} 
		catch (Exception e) {//Catch exception if any
			log.error("Error creating total throughput csv: ", e);
		}
   	}
   

   	private static boolean isDouble(String string) {
	    try {
	        Double.valueOf(string);
	        return true;
	    } catch (NumberFormatException e) {
	        return false;
	    }
	}
	
	private static double addValue(String value){
		if (isDouble(value)){
			return Double.valueOf(value);
		}
		return 0;
	}
   
}

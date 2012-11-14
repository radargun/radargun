package org.radargun.stages;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.DataInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.text.DecimalFormat;
import java.text.Format;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.StringTokenizer;

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
   private static final SimpleDateFormat DATEFORMAT = new SimpleDateFormat("HH:mm:ss,SSS");

   private String targetDir = "reports";
   private int chartWidth = 800;
   private int chartHeight = 600;
   private boolean generateIntervalTimeData = false;
   private Set<Integer> ignore;

   public boolean execute() {
      @SuppressWarnings("unchecked")
      Map<Integer, List<Stats>> allResults = (Map<Integer, List<Stats>>) masterState.get(BackgroundStats.NAME);
      if (allResults == null) {
         log.error("Could not find BackgroundStats results on the master. Master's state is  " + masterState);
         return false;
      }
      if (allResults.size() == 0) {
         log.warn("Nothing to report!");
         return false;
      }
      if (masterState.getSlavesCountForCurrentStage() != allResults.size()) {
         log.error("We're missing statistics from some slaves");
         return false;
      }
      
      List<List<Stats>> results = new ArrayList<List<Stats>>();
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
         File csvTotalThroughput = new File(subdir, "total-throughput.csv");
         File csvAvgRespTimes = new File(subdir, "avg-response-times.csv");
         File csvEntryCounts = new File(subdir, "entry-counts.csv");
         File csvNullGets = new File(subdir, "null-responses.csv");
         File csvErrors = new File(subdir, "errors.csv");
         File csvTotals = new File(subdir, "total.csv");

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
         if (generateIntervalTimeData) {
            generateMultiSlaveCsv(new File(subdir, "interval-time-begin.csv"), results, maxResultSize,
                  new StatGetter() {
                     @Override
                     public String getStat(Stats cell) {
                        return humanReadableTime(cell.getIntervalBeginTime());
                     }
                  });
            generateMultiSlaveCsv(new File(subdir, "interval-time-end.csv"), results, maxResultSize, new StatGetter() {
               @Override
               public String getStat(Stats cell) {
                  return humanReadableTime(cell.getIntervalEndTime());
               }
            });
            generateMultiSlaveCsv(new File(subdir, "interval-duration.csv"), results, maxResultSize, new StatGetter() {
               @Override
               public String getStat(Stats cell) {
                  return Long.toString(cell.getDuration());
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

   private void generateTotalsCsv(File file, List<List<Stats>> results, int maxResultSize) throws Exception {
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
      List<Stats> row = new ArrayList<Stats>();
      List<Stats> rowAll = new ArrayList<Stats>();
      for (int i = 0; i < maxResultSize; i++) {
         row.clear();
         rowAll.clear();
         for (int j = 0; j < results.size(); j++) {
            List<Stats> statList = results.get(j);
            if (i < statList.size()) {
               if (statList.get(i).isNodeUp()) {
                  row.add(statList.get(i));
               }
               rowAll.add(statList.get(i));
            }
         }
         w.print(i);

         long beginMin = Stats.getIntervalBeginMin(rowAll);
         long beginMax = Stats.getIntervalBeginMax(rowAll);
         long endMin = Stats.getIntervalEndMin(rowAll);
         long endMax = Stats.getIntervalEndMax(rowAll);

         w.print(CSVChart.SEPARATOR);
         w.print(humanReadableTime(beginMin));
         w.print(CSVChart.SEPARATOR);
         w.print(humanReadableTime(endMax));
         w.print(CSVChart.SEPARATOR);
         w.print(beginMax - beginMin);
         w.print(CSVChart.SEPARATOR);
         w.print(endMax - endMin);
         w.print(CSVChart.SEPARATOR);
         w.print(Stats.getTotalThroughput(row));
         w.print(CSVChart.SEPARATOR);
         w.print(Stats.getAvgThroughput(row));
         w.print(CSVChart.SEPARATOR);
         w.print(ffcsv(Stats.getAvgRespTime(row)));
         w.print(CSVChart.SEPARATOR);
         w.print(Stats.getMaxRespTime(row));
         w.print(CSVChart.SEPARATOR);
         w.print(Stats.getTotalCacheSize(row));
         w.print(CSVChart.SEPARATOR);
         w.print(Stats.getTotalErrors(row));
         w.print(CSVChart.SEPARATOR);
         w.print(Stats.getTotalNullRequests(row));
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
      String getStat(Stats cell);
   }

   public void setTargetDir(String targetDir) {
      this.targetDir = targetDir;
   }

   public void setChartHeight(int chartHeight) {
      this.chartHeight = chartHeight;
   }

   public void setChartWidth(int chartWidth) {
      this.chartWidth = chartWidth;
   }

   public void setGenerateIntervalTimeData(boolean generateIntervalTimeData) {
      this.generateIntervalTimeData = generateIntervalTimeData;
   }
   
   public void setIgnore(String list) {
      StringTokenizer tokenizer = new StringTokenizer(list, ",");
      Set<Integer> ignored = new HashSet<Integer>();
      try {
         while (tokenizer.hasMoreTokens()) {         
            ignored.add(Integer.parseInt(tokenizer.nextToken().trim()));
         }
         this.ignore = ignored;
      } catch (NumberFormatException e) {
         log.error("Failed to parse ignore list: " + list);
      }
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

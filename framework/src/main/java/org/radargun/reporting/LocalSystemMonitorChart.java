package org.radargun.reporting;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.radargun.local.ReportDesc;
import org.radargun.local.ReportItem;
import org.radargun.stages.GenerateChartStage;
import org.radargun.sysmonitor.AbstractActivityMonitor;
import org.radargun.sysmonitor.CpuUsageMonitor;
import org.radargun.sysmonitor.GcMonitor;
import org.radargun.sysmonitor.LocalJmxMonitor;
import org.radargun.sysmonitor.MemoryUsageMonitor;
import org.radargun.utils.Utils;

import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.math.BigDecimal;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * @author Mircea Markus
 */
public class LocalSystemMonitorChart {

   private static Log log = LogFactory.getLog(LocalSystemMonitorChart.class);
   private StringBuilder reportCsvContent;

   final Map<String, LocalJmxMonitor> sysMonitors;

   String reportPrefix;

   public LocalSystemMonitorChart(Map<String, LocalJmxMonitor> sysMonitors) {
      this.sysMonitors = new HashMap<String, LocalJmxMonitor>(sysMonitors);
   }

   public void generate(ReportDesc reportDesc) {

      if (!reportDesc.isIncludeAll()) {
         if (reportDesc.getItems().isEmpty()) {
            log.info("No reports defined, not generating system monitor graphs.");
            return;
         }
         Map<String, LocalJmxMonitor> filter = new HashMap<String, LocalJmxMonitor>();
         for (ReportItem reportItem : reportDesc.getItems()) {
            for (Map.Entry<String, LocalJmxMonitor> e : sysMonitors.entrySet()) {
               LocalJmxMonitor m = e.getValue();
               if (reportItem.matches(m.getProductName(), m.getConfigName())) {
                  filter.put(e.getKey(), e.getValue());
               }
            }
         }
         sysMonitors.clear();
         sysMonitors.putAll(filter);
         reportPrefix = reportDesc.getReportName();
      } else {
         reportPrefix = "All";
      }
      generateCpu();
      generateGc();
      generateMemory();
   }

   private void generateMemory() {
      this.reportCsvContent = new StringBuilder().append("NODE,TIME,MEASUREMENT\n");
      ClusterReport lcr = new ClusterReport();
      lcr.init("Time(sec)", "Memory(Mb)", "Memory consumption", "");
      for (String s : sysMonitors.keySet()) {
         MemoryUsageMonitor memMonitor = sysMonitors.get(s).getMemoryMonitor();
         memMonitor.convertToMb();
         populateGraph(lcr, "mem-" + s, memMonitor);
      }
      generateReport(lcr, "memory_usage");
   }

   private void generateCpu() {
      this.reportCsvContent = new StringBuilder().append("NODE,TIME,MEASUREMENT\n");
      ClusterReport lcr = new ClusterReport();
      lcr.init("Time(sec)", "CPU", "CPU Usage (%)", "");
      for (String s : sysMonitors.keySet()) {
         CpuUsageMonitor cpuMonitor = sysMonitors.get(s).getCpuMonitor();
         populateGraph(lcr, "cpu-" + s, cpuMonitor);
      }
      generateReport(lcr, "cpu_usage");
   }

   private void generateGc() {
      this.reportCsvContent = new StringBuilder().append("NODE,TIME,MEASUREMENT\n");
      ClusterReport lcr = new ClusterReport();
      lcr.init("Time(sec)", "GC", "GC Usage (%)", "");
      for (String s : sysMonitors.keySet()) {
         GcMonitor gcMonitor = sysMonitors.get(s).getGcMonitor();
         populateGraph(lcr, "gc-" + s, gcMonitor);
      }
      generateReport(lcr, "gc_usage");
   }

   private void populateGraph(ClusterReport lcr, String s, AbstractActivityMonitor activityMonitor) {
      int measuringFrequencySecs = (int) TimeUnit.MILLISECONDS.toSeconds(LocalJmxMonitor.MEASURING_FREQUENCY);
      LinkedHashMap<Integer, BigDecimal> graphData = activityMonitor.formatForGraph(measuringFrequencySecs, 25);
      for (Map.Entry<Integer, BigDecimal> e : graphData.entrySet()) {
         lcr.addCategory(s, e.getKey(), e.getValue());
         reportCsvContent.append('\n').append(s + "," + e.getKey() + "," + e.getValue());
      }
   }

   private void generateReport(ClusterReport lcr, String fileNameNoExtension) {
      try {
         createOutputFile(reportPrefix + "-" + fileNameNoExtension + ".csv", reportCsvContent);
         LineReportGenerator.generate(lcr, GenerateChartStage.REPORTS, reportPrefix + "-" + fileNameNoExtension);
      } catch (IOException e1) {
         log.error("Failed to write CSV file", e1);
      } catch (Exception e) {
         log.error(e.getMessage(), e);
      }
   }

   private void createOutputFile(String fileName, StringBuilder reportCsvContent) throws IOException {
      File parentDir = new File(GenerateChartStage.REPORTS);
      if (!parentDir.exists()) {
         if (!parentDir.mkdirs())
            throw new RuntimeException(parentDir.getAbsolutePath() + " does not exist and could not be created!");
      }

      File reportFile = Utils.createOrReplaceFile(parentDir, fileName);
      if (!reportFile.exists()) {
         throw new IllegalStateException(reportFile.getAbsolutePath()
               + " was deleted? Not allowed to delete report file during test run!");
      }
      PrintWriter writer = null;
      try {
         writer = new PrintWriter(reportFile);
         writer.append(reportCsvContent.toString());
      } finally {
         if (writer != null)
            writer.close();
      }
   }
}

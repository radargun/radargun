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
      generateCpuAndGc();
      generateMemory();
   }

   private void generateMemory() {
      LineClusterReport lcr = new LineClusterReport();
      lcr.init("Time", "Memory(Mb)", "Memory consumption", "");
      for (String s : sysMonitors.keySet()) {
         MemoryUsageMonitor memMonitor = sysMonitors.get(s).getMemoryMonitor();
         memMonitor.convertToMb();
         populateGraph(lcr, s, memMonitor);
      }
      generateReport(lcr, "memory_usage");
   }

   private void generateCpuAndGc() {
      LineClusterReport lcr = new LineClusterReport();
      lcr.init("Time", "CPU&GC", "CPU & GC Usage (%)", "");
      for (String s : sysMonitors.keySet()) {
         CpuUsageMonitor cpuMonitor = sysMonitors.get(s).getCpuMonitor();
         populateGraph(lcr, "cpu-" + s, cpuMonitor);
      }
      for (String s : sysMonitors.keySet()) {
         GcMonitor gcMonitor = sysMonitors.get(s).getGcMonitor();
         populateGraph(lcr, "gc-" + s, gcMonitor);
      }
      generateReport(lcr, "cpu_gc_usage");
   }

   private void populateGraph(LineClusterReport lcr, String s, AbstractActivityMonitor activityMonitor) {
      int measuringFrequencySecs = (int) TimeUnit.MILLISECONDS.toSeconds(LocalJmxMonitor.MEASURING_FREQUENCY);
      LinkedHashMap<Integer,BigDecimal> graphData = activityMonitor.formatForGraph(measuringFrequencySecs);
      for (Map.Entry<Integer, BigDecimal> e : graphData.entrySet()) {
         lcr.addCategory(s, e.getKey(), e.getValue());
      }
   }

   private void generateReport(LineClusterReport lcr, String fileNameNoExtension) {
      lcr.setReportFile(GenerateChartStage.REPORTS, reportPrefix + "-" + fileNameNoExtension);
      try {
         lcr.generate();
      } catch (Exception e) {
         log.error(e.getMessage(), e);
      }
   }
}

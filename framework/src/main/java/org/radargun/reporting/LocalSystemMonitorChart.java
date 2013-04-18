package org.radargun.reporting;

import java.io.IOException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.jfree.data.time.TimeSeries;
import org.jfree.data.time.TimeSeriesDataItem;
import org.radargun.local.ReportDesc;
import org.radargun.local.ReportItem;
import org.radargun.stages.GenerateChartStage;
import org.radargun.sysmonitor.AbstractActivityMonitor;
import org.radargun.sysmonitor.CpuUsageMonitor;
import org.radargun.sysmonitor.GcMonitor;
import org.radargun.sysmonitor.LocalJmxMonitor;
import org.radargun.sysmonitor.MemoryUsageMonitor;
import org.radargun.sysmonitor.NetworkBytesInMonitor;
import org.radargun.sysmonitor.NetworkBytesOutMonitor;
import org.radargun.utils.Utils;

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
      generateNetwork();
   }

   private void generateMemory() {
      this.reportCsvContent = new StringBuilder().append("NODE,TIME,MEASUREMENT\n");
      ClusterTimeSeriesReport timeReport = new ClusterTimeSeriesReport();
      timeReport.init("Time(sec)", "Memory(Mb)", "Memory consumption", "");
      String[] sortedKeys = sysMonitors.keySet().toArray(new String[0]);
      Arrays.sort(sortedKeys);
      for (String s : sortedKeys) {
         MemoryUsageMonitor memMonitor = sysMonitors.get(s).getMemoryMonitor();
         memMonitor.convertToMb();
         populateGraph(timeReport, "mem-" + s, memMonitor);
      }
      generateReport(timeReport, "memory_usage");
   }

   private void generateCpu() {
      this.reportCsvContent = new StringBuilder().append("NODE,TIME,MEASUREMENT\n");
      ClusterTimeSeriesReport timeReport = new ClusterTimeSeriesReport();
      timeReport.init("Time(sec)", "CPU", "CPU Usage (%)", "");
      String[] sortedKeys = sysMonitors.keySet().toArray(new String[0]);
      Arrays.sort(sortedKeys);
      for (String s : sortedKeys) {
         CpuUsageMonitor cpuMonitor = sysMonitors.get(s).getCpuMonitor();
         populateGraph(timeReport, "cpu-" + s, cpuMonitor);
      }
      generateReport(timeReport, "cpu_usage");
   }

   private void generateGc() {
      this.reportCsvContent = new StringBuilder().append("NODE,TIME,MEASUREMENT\n");
      ClusterTimeSeriesReport timeReport = new ClusterTimeSeriesReport();
      timeReport.init("Time(sec)", "GC", "GC Usage (%)", "");
      String[] sortedKeys = sysMonitors.keySet().toArray(new String[0]);
      Arrays.sort(sortedKeys);
      for (String s : sortedKeys) {
         GcMonitor gcMonitor = sysMonitors.get(s).getGcMonitor();
         populateGraph(timeReport, "gc-" + s, gcMonitor);
      }
      generateReport(timeReport, "gc_usage");
   }

   private void generateNetwork() {
      this.reportCsvContent = new StringBuilder().append("NODE,TIME,MEASUREMENT\n");
      ClusterTimeSeriesReport timeReport = new ClusterTimeSeriesReport();
      timeReport.init("Time(sec)", "Network(bytes)", "Network traffic", "");
      String[] sortedKeys = sysMonitors.keySet().toArray(new String[0]);
      Arrays.sort(sortedKeys);
      for (String s : sortedKeys) {
         NetworkBytesInMonitor netInMonitor = sysMonitors.get(s).getNetworkBytesInMonitor();
         populateGraph(timeReport, "network-inbound-" + s, netInMonitor);
      }
      for (String s : sortedKeys) {
         NetworkBytesOutMonitor netOutMonitor = sysMonitors.get(s).getNetworkBytesOutMonitor();
         populateGraph(timeReport, "network-outbound-" + s, netOutMonitor);
      }
      generateReport(timeReport, "network_usage");
   }

   private void populateGraph(ClusterTimeSeriesReport timeReport, String s, AbstractActivityMonitor activityMonitor) {
      TimeSeries monitorData = timeReport.generateSeries(s, activityMonitor);
      for (Object item : monitorData.getItems()) {
         TimeSeriesDataItem tsdi = (TimeSeriesDataItem) item;
         reportCsvContent.append('\n').append(s + "," + tsdi.getPeriod() + "," + tsdi.getValue());
      }
      reportCsvContent.append('\n');
      timeReport.addSeries(monitorData);
   }

   private void generateReport(ClusterTimeSeriesReport timeReport, String fileNameNoExtension) {
      try {
         Utils.createOutputFile(reportPrefix + "-" + fileNameNoExtension + ".csv", reportCsvContent.toString());
         TimeSeriesReportGenerator.generate(timeReport, GenerateChartStage.REPORTS, reportPrefix + "-"
               + fileNameNoExtension);
      } catch (IOException e1) {
         log.error("Failed to write CSV file", e1);
      } catch (Exception e) {
         log.error(e.getMessage(), e);
      }
   }

}

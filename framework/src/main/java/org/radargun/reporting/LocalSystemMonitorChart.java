package org.radargun.reporting;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Map;
import java.util.TreeMap;

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
import org.radargun.sysmonitor.NetworkBytesMonitor;
import org.radargun.utils.Utils;

/**
 * @author Mircea Markus
 */
public class LocalSystemMonitorChart {

   private static Log log = LogFactory.getLog(LocalSystemMonitorChart.class);
   private StringBuilder reportHeader;
   private ArrayList<String> reportStrings;

   final Map<String, LocalJmxMonitor> sysMonitors;

   String reportPrefix;

   public LocalSystemMonitorChart(Map<String, LocalJmxMonitor> sysMonitors) {
      this.sysMonitors = new TreeMap<String, LocalJmxMonitor>(sysMonitors);
   }

   public void generate(ReportDesc reportDesc) {

      if (!reportDesc.isIncludeAll()) {
         if (reportDesc.getItems().isEmpty()) {
            log.info("No reports defined, not generating system monitor graphs.");
            return;
         }
         Map<String, LocalJmxMonitor> filter = new TreeMap<String, LocalJmxMonitor>();
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
      reportHeader = new StringBuilder("Second");
      reportStrings = null;
      ClusterTimeSeriesReport timeReport = new ClusterTimeSeriesReport();
      timeReport.init("Time(sec)", "Memory(Mb)", "Memory consumption", "");
      for (String s : sysMonitors.keySet()) {
         reportHeader.append(", mem-" + s);
         MemoryUsageMonitor memMonitor = sysMonitors.get(s).getMemoryMonitor();
         memMonitor.convertToMb();
         populateGraph(timeReport, "mem-" + s, memMonitor);
      }
      generateReport(timeReport, "memory_usage");
   }

   private void generateCpu() {
      reportHeader = new StringBuilder("Second");
      reportStrings = null;
      ClusterTimeSeriesReport timeReport = new ClusterTimeSeriesReport();
      timeReport.init("Time(sec)", "CPU", "CPU Usage (%)", "");
      for (String s : sysMonitors.keySet()) {
         reportHeader.append(", cpu-" + s);
         CpuUsageMonitor cpuMonitor = sysMonitors.get(s).getCpuMonitor();
         populateGraph(timeReport, "cpu-" + s, cpuMonitor);
      }
      generateReport(timeReport, "cpu_usage");
   }

   private void generateGc() {
      reportHeader = new StringBuilder("Second");
      reportStrings = null;
      ClusterTimeSeriesReport timeReport = new ClusterTimeSeriesReport();
      timeReport.init("Time(sec)", "GC", "GC Usage (%)", "");
      for (String s : sysMonitors.keySet()) {
         reportHeader.append(", gc-" + s);
         GcMonitor gcMonitor = sysMonitors.get(s).getGcMonitor();
         populateGraph(timeReport, "gc-" + s, gcMonitor);
      }
      generateReport(timeReport, "gc_usage");
   }

   private void generateNetwork() {
      reportHeader = new StringBuilder("Second");
      reportStrings = null;
      ClusterTimeSeriesReport timeReport = new ClusterTimeSeriesReport();
      timeReport.init("Time(sec)", "Network(bytes)", "Network traffic", "");
      for (String s : sysMonitors.keySet()) {
         reportHeader.append(", network-inbound-" + s + ", network-outbound-" + s);
         NetworkBytesMonitor netInMonitor = sysMonitors.get(s).getNetworkBytesInMonitor();
         populateGraph(timeReport, "network-inbound-" + s, netInMonitor);
         NetworkBytesMonitor netOutMonitor = sysMonitors.get(s).getNetworkBytesOutMonitor();
         populateGraph(timeReport, "network-outbound-" + s, netOutMonitor);
      }
      generateReport(timeReport, "network_usage");
   }

   private void populateGraph(ClusterTimeSeriesReport timeReport, String s, AbstractActivityMonitor activityMonitor) {
      TimeSeries monitorData = timeReport.generateSeries(s, activityMonitor);
      int counter = 0;
      if (reportStrings == null) {
         reportStrings = new ArrayList<String>();
         for (Object item : monitorData.getItems()) {
            TimeSeriesDataItem tsdi = (TimeSeriesDataItem) item;
            reportStrings.add(counter++ + "," + tsdi.getValue());
         }
      } else {
         for (Object item : monitorData.getItems()) {
            TimeSeriesDataItem tsdi = (TimeSeriesDataItem) item;
            reportStrings.set(counter, reportStrings.get(counter) + "," + tsdi.getValue());
            counter++;
         }
      }
      timeReport.addSeries(monitorData);
   }

   private void generateReport(ClusterTimeSeriesReport timeReport, String fileNameNoExtension) {
      try {
         Utils.createOutputFile(reportPrefix + "-" + fileNameNoExtension + ".csv", generateReportCSV());
         TimeSeriesReportGenerator.generate(timeReport, GenerateChartStage.REPORTS, reportPrefix + "-"
               + fileNameNoExtension);
      } catch (IOException e1) {
         log.error("Failed to write CSV file", e1);
      } catch (Exception e) {
         log.error(e.getMessage(), e);
      }
   }

   private String generateReportCSV() {
      StringBuilder reportCsvContent = new StringBuilder(reportHeader + "\n");
      for (String reportItem : reportStrings) {
         reportCsvContent.append(reportItem + "\n");
      }
      return reportCsvContent.toString();
   }

}

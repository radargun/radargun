/* 
 * JBoss, Home of Professional Open Source
 * Copyright 2013 Red Hat Inc. and/or its affiliates and other contributors
 * as indicated by the @author tags. All rights reserved.
 * See the copyright.txt in the distribution for a
 * full listing of individual contributors.
 *
 * This copyrighted material is made available to anyone wishing to use,
 * modify, copy, or redistribute it subject to the terms and conditions
 * of the GNU Lesser General Public License, v. 2.1.
 * This program is distributed in the hope that it will be useful, but WITHOUT A
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A
 * PARTICULAR PURPOSE.  See the GNU Lesser General Public License for more details.
 * You should have received a copy of the GNU Lesser General Public License,
 * v.2.1 along with this distribution; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston,
 * MA  02110-1301, USA.
 */
package org.radargun.stages;

import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import org.radargun.config.Stage;
import org.radargun.reporting.ClusterReport;
import org.radargun.reporting.LineReportGenerator;
import org.radargun.sysmonitor.AbstractActivityMonitor;
import org.radargun.sysmonitor.CpuUsageMonitor;
import org.radargun.sysmonitor.GcMonitor;
import org.radargun.sysmonitor.LocalJmxMonitor;
import org.radargun.sysmonitor.MemoryUsageMonitor;
import org.radargun.utils.Utils;

/**
 *
 * Generate charts for JVM statistics on each slave node.
 *
 * @author Alan Field &lt;afield@redhat.com&gt;
 */
@Stage(doc = "Generate charts for JVM statistics on each slave node.")
public class ReportJVMMonitorStage extends AbstractMasterStage {

   Map<String, LocalJmxMonitor> sysMonitors;
   String reportPrefix;

   @SuppressWarnings("unchecked")
   @Override
   public boolean execute() throws Exception {
      this.sysMonitors = (Map<String, LocalJmxMonitor>) masterState.get(StartJVMMonitorStage.monitorKey);
      if (!sysMonitors.isEmpty()) {
         masterState.remove(StartJVMMonitorStage.monitorKey);
         LocalJmxMonitor monitor = sysMonitors.values().iterator().next();
         reportPrefix = monitor.getProductName() + " (" + monitor.getConfigName() + ") on " + sysMonitors.size()
               + " node(s)";
         this.generateCpu();
         this.generateGc();
         this.generateMemory();
         return true;
      }
      this.log.error("Could not retrieve the JVM monitors");
      return false;
   }

   private void generateMemory() {
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
      ClusterReport lcr = new ClusterReport();
      lcr.init("Time(sec)", "CPU", "CPU Usage (%)", "");
      for (String s : sysMonitors.keySet()) {
         CpuUsageMonitor cpuMonitor = sysMonitors.get(s).getCpuMonitor();
         populateGraph(lcr, "cpu-" + s, cpuMonitor);
      }
      generateReport(lcr, "cpu_usage");
   }

   private void generateGc() {
      ClusterReport lcr = new ClusterReport();
      lcr.init("Time(sec)", "GC", "GC Usage (%)", "");
      for (String s : sysMonitors.keySet()) {
         GcMonitor gcMonitor = sysMonitors.get(s).getGcMonitor();
         populateGraph(lcr, "gc-" + s, gcMonitor);
      }
      generateReport(lcr, "gc_usage");
   }

   private void populateGraph(ClusterReport lcr, String s, AbstractActivityMonitor activityMonitor) {
      StringBuilder reportCsvContent = new StringBuilder();
      reportCsvContent.append("NODE,TIME,MEASUREMENT\n");
      int measuringFrequencySecs = (int) TimeUnit.MILLISECONDS.toSeconds(LocalJmxMonitor.MEASURING_FREQUENCY);
      LinkedHashMap<Integer, BigDecimal> graphData = activityMonitor.formatForGraph(measuringFrequencySecs, 25);
      for (Map.Entry<Integer, BigDecimal> e : graphData.entrySet()) {
         lcr.addCategory(s, e.getKey(), e.getValue());
         reportCsvContent.append('\n').append(s + "," + e.getKey() + "," + e.getValue());
      }

      try {
         createOutputFile(reportPrefix + "-" + s + ".csv", reportCsvContent);
      } catch (IOException e1) {
         log.error("Failed to write CSV file", e1);
      }
   }

   private void generateReport(ClusterReport lcr, String fileNameNoExtension) {
      try {

         LineReportGenerator.generate(lcr, GenerateChartStage.REPORTS, reportPrefix + "-" + fileNameNoExtension);
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
         throw new IllegalStateException(reportFile.getAbsolutePath() + " was deleted? Not allowed to delete report file during test run!");
      }
      PrintWriter writer = null;
      try {
         writer = new PrintWriter(reportFile);
         writer.append(reportCsvContent.toString());
      } finally {
         if (writer != null) writer.close();
      }

   }
}
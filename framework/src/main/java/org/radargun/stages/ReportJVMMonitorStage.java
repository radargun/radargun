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

import java.util.Map;

import org.radargun.config.Property;
import org.radargun.config.Stage;
import org.radargun.local.ReportDesc;
import org.radargun.reporting.LocalSystemMonitorChart;
import org.radargun.stages.monitor.StartJVMMonitorStage;
import org.radargun.sysmonitor.LocalJmxMonitor;

/**
 * 
 * Generate charts for JVM statistics on each slave node.
 * 
 * @author Alan Field &lt;afield@redhat.com&gt;
 */
@Stage(doc = "Generate charts for JVM statistics on each slave node.")
@Deprecated
public class ReportJVMMonitorStage extends AbstractMasterStage {

   @Property(doc = "A prefix that will be added to the report name. Default is null.")
   private String reportPrefix = null;

   @Override
   public boolean execute() throws Exception {
      @SuppressWarnings("unchecked")
      Map<String, LocalJmxMonitor> sysMonitors = (Map<String, LocalJmxMonitor>) masterState
            .get(StartJVMMonitorStage.MONITOR_KEY);
      if (!sysMonitors.isEmpty()) {
         ReportDesc reportDesc = new ReportDesc();
         LocalJmxMonitor monitor = sysMonitors.values().iterator().next();
         reportDesc.addReportItem(monitor.getConfigName());
         if (reportPrefix == null) {
            reportDesc.setReportName(monitor.getConfigName() + " on " + sysMonitors.size() + " node(s)");
         } else {
            reportDesc.setReportName(reportPrefix + "-" + monitor.getConfigName() + " on " + sysMonitors.size() + " node(s)");
         }
         new LocalSystemMonitorChart(sysMonitors).generate(reportDesc);
         masterState.remove(StartJVMMonitorStage.MONITOR_KEY);
         return true;
      }
      this.log.error("Could not retrieve the JVM monitors");
      return false;
   }
}
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

import java.util.HashMap;
import java.util.List;

import org.radargun.DistStageAck;
import org.radargun.config.Stage;
import org.radargun.state.MasterState;
import org.radargun.sysmonitor.LocalJmxMonitor;

/**
 * 
 * Stop collecting JVM statistics on each slave node and return collected statistics to the master node.
 * 
 * @author Alan Field &lt;afield@redhat.com&gt;
 */
@Stage(doc = "Stop collecting JVM statistics on each slave node and return collected statistics to the master node.")
public class StopJVMMonitorStage extends AbstractDistStage {

   @Override
   public DistStageAck executeOnSlave() {
      DefaultDistStageAck ack = newDefaultStageAck();
      LocalJmxMonitor monitor = (LocalJmxMonitor) slaveState.get(StartJVMMonitorStage.MONITOR_KEY);
      if (monitor != null) {
         monitor.stopMonitoringLocal();
         ack.setPayload(monitor);
         slaveState.remove(StartJVMMonitorStage.MONITOR_KEY);
      } else {
         ack.setError(true);
         ack.setErrorMessage("No JVMMonitor object found on slave: " + this.slaveIndex);
      }
      return ack;
   }

   @Override
   public boolean processAckOnMaster(List<DistStageAck> acks, MasterState masterState) {
      log.info("StopJVMMonitor.processAckOnMaster");
      HashMap<String, LocalJmxMonitor> sysMonitors = new HashMap<String, LocalJmxMonitor>();
      for (DistStageAck ack : acks) {
         DefaultDistStageAck dack = (DefaultDistStageAck) ack;
         LocalJmxMonitor monitor = (LocalJmxMonitor) dack.getPayload();
         sysMonitors.put("slave-" + dack.getSlaveIndex(), monitor);
      }
      masterState.put(StartJVMMonitorStage.MONITOR_KEY, sysMonitors);
      return true;
   }
}

package org.radargun.stages.monitor;

import org.radargun.DistStageAck;
import org.radargun.config.Stage;
import org.radargun.stages.AbstractDistStage;
import org.radargun.sysmonitor.LocalJmxMonitor;

/**
 * 
 * Stop collecting JVM statistics on each slave node and return collected statistics to the master node.
 * 
 * @author Alan Field &lt;afield@redhat.com&gt;
 */
@Stage(doc = "Stop collecting JVM statistics on each slave node and return collected statistics to the master node.")
public class JVMMonitorStopStage extends AbstractDistStage {

   @Override
   public DistStageAck executeOnSlave() {
      LocalJmxMonitor monitor = (LocalJmxMonitor) slaveState.get(JVMMonitorStartStage.MONITOR_KEY);
      if (monitor != null) {
         monitor.stopMonitoringLocal();
         slaveState.removeServiceListener(monitor);
         return successfulResponse();
      } else {
         return errorResponse("No JVMMonitor object found on slave: " + slaveState.getSlaveIndex());
      }
   }
}

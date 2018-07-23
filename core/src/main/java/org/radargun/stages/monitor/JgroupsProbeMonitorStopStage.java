package org.radargun.stages.monitor;

import org.radargun.DistStageAck;
import org.radargun.config.Stage;
import org.radargun.stages.AbstractDistStage;
import org.radargun.sysmonitor.ProbeSlaveMonitor;

/**
 *
 * Stop collecting probe statistics on each slave node.
 * {@link ProbeSlaveMonitor}
 *
 * @author Diego Lovison &lt;dlovison@redhat.com&gt;
 */
@Stage(name="jgroups-probe-monitor-stop", doc = "Stop collecting jgroups statistics locally in each slave node.")
public class JgroupsProbeMonitorStopStage extends AbstractDistStage {

   @Override
   public DistStageAck executeOnSlave() {
      ProbeSlaveMonitor slaveMonitors = (ProbeSlaveMonitor) slaveState.get(ProbeSlaveMonitor.MONITORS);
      if (slaveMonitors != null) {
         slaveMonitors.stop();
         return successfulResponse();
      } else {
         return errorResponse("No Monitors object found on slave: " + slaveState.getSlaveIndex());
      }
   }
}

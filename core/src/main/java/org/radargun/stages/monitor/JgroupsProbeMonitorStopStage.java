package org.radargun.stages.monitor;

import org.radargun.DistStageAck;
import org.radargun.config.Stage;
import org.radargun.stages.AbstractDistStage;
import org.radargun.sysmonitor.ProbeWorkerMonitor;

/**
 *
 * Stop collecting probe statistics on each worker node.
 * {@link ProbeWorkerMonitor}
 *
 * @author Diego Lovison &lt;dlovison@redhat.com&gt;
 */
@Stage(name="jgroups-probe-monitor-stop", doc = "Stop collecting jgroups statistics locally in each worker node.")
public class JgroupsProbeMonitorStopStage extends AbstractDistStage {

   @Override
   public DistStageAck executeOnWorker() {
      ProbeWorkerMonitor workerMonitors = (ProbeWorkerMonitor) workerState.get(ProbeWorkerMonitor.MONITORS);
      if (workerMonitors != null) {
         workerMonitors.stop();
         return successfulResponse();
      } else {
         return errorResponse("No Monitors object found on worker: " + workerState.getWorkerIndex());
      }
   }
}

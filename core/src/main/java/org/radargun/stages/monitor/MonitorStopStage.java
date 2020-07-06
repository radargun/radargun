package org.radargun.stages.monitor;

import java.util.List;
import org.radargun.DistStageAck;
import org.radargun.StageResult;
import org.radargun.config.Stage;
import org.radargun.stages.AbstractDistStage;
import org.radargun.sysmonitor.MainMonitors;
import org.radargun.sysmonitor.SystemWorkerMonitor;

/**
 *
 * Stop collecting JVM statistics on each worker node and return collected statistics to the main node.
 *
 * @author Alan Field &lt;afield@redhat.com&gt;
 */
@Stage(doc = "Stop collecting statistics on each worker node and return collected statistics to the main node.", deprecatedName = "jvm-monitor-stop")
public class MonitorStopStage extends AbstractDistStage {

   @Override
   public DistStageAck executeOnWorker() {
      SystemWorkerMonitor workerMonitors = (SystemWorkerMonitor) workerState.get(SystemWorkerMonitor.MONITORS);
      if (workerMonitors != null) {
         workerMonitors.stop();
         return successfulResponse();
      } else {
         return errorResponse("No Monitors object found on worker: " + workerState.getWorkerIndex());
      }
   }

   @Override
   public StageResult processAckOnMain(List<DistStageAck> acks) {
      MainMonitors mainMonitors = (MainMonitors) mainState.get(MainMonitors.MONITORS);
      if (mainMonitors != null) {
         mainMonitors.stop();
         return StageResult.SUCCESS;
      } else {
         log.warn("No monitor found on main!");
         return errorResult();
      }
   }
}

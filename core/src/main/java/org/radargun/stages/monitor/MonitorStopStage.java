package org.radargun.stages.monitor;

import java.util.List;
import org.radargun.DistStageAck;
import org.radargun.StageResult;
import org.radargun.config.Stage;
import org.radargun.stages.AbstractDistStage;
import org.radargun.sysmonitor.MasterMonitors;
import org.radargun.sysmonitor.SystemSlaveMonitor;

/**
 *
 * Stop collecting JVM statistics on each slave node and return collected statistics to the master node.
 *
 * @author Alan Field &lt;afield@redhat.com&gt;
 */
@Stage(doc = "Stop collecting statistics on each slave node and return collected statistics to the master node.", deprecatedName = "jvm-monitor-stop")
public class MonitorStopStage extends AbstractDistStage {

   @Override
   public DistStageAck executeOnSlave() {
      SystemSlaveMonitor slaveMonitors = (SystemSlaveMonitor) slaveState.get(SystemSlaveMonitor.MONITORS);
      if (slaveMonitors != null) {
         slaveMonitors.stop();
         return successfulResponse();
      } else {
         return errorResponse("No Monitors object found on slave: " + slaveState.getSlaveIndex());
      }
   }

   @Override
   public StageResult processAckOnMaster(List<DistStageAck> acks) {
      MasterMonitors masterMonitors = (MasterMonitors) masterState.get(MasterMonitors.MONITORS);
      if (masterMonitors != null) {
         masterMonitors.stop();
         return StageResult.SUCCESS;
      } else {
         log.warn("No monitor found on master!");
         return errorResult();
      }
   }
}

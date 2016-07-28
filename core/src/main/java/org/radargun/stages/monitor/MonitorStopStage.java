package org.radargun.stages.monitor;

import org.radargun.DistStageAck;
import org.radargun.config.Stage;
import org.radargun.stages.AbstractDistStage;
import org.radargun.sysmonitor.Monitors;

/**
 * Stop collecting JVM statistics on each slave node and return collected statistics to the master node.
 *
 * @author Alan Field &lt;afield@redhat.com&gt;
 */
@Stage(doc = "Stop collecting statistics on each slave node and return collected statistics to the master node.", deprecatedName = "jvm-monitor-stop")
public class MonitorStopStage extends AbstractDistStage {

   @Override
   public DistStageAck executeOnSlave() {
      Monitors monitor = (Monitors) slaveState.get(Monitors.MONITORS);
      if (monitor != null) {
         monitor.stop();
         return successfulResponse();
      } else {
         return errorResponse("No Monitors object found on slave: " + slaveState.getSlaveIndex());
      }
   }
}

package org.radargun.stages.monitor;

import java.io.IOException;

import org.radargun.DistStageAck;
import org.radargun.config.Property;
import org.radargun.config.Stage;
import org.radargun.stages.AbstractJGroupsProbeStage;
import org.radargun.sysmonitor.Monitor;
import org.radargun.sysmonitor.ProbeWorkerMonitor;
import org.radargun.utils.TimeConverter;

/**
 *
 * Starts collecting probe statistics on each worker node.
 * {@link ProbeWorkerMonitor}
 *
 * @author Diego Lovison &lt;dlovison@redhat.com&gt;
 */
@Stage(name="jgroups-probe-monitor-start", doc = "Starts collecting jgroups statistics locally in each worker node.")
public class JgroupsProbeMonitorStartStage extends AbstractJGroupsProbeStage {

   @Property(doc = "Period of statistics collection. The default is 60 seconds.", converter = TimeConverter.class)
   private long period = 60_000;

   @Override
   public DistStageAck executeOnWorker() {

      ProbeWorkerMonitor workerMonitors = workerState.get(ProbeWorkerMonitor.MONITORS) == null ? new ProbeWorkerMonitor(workerState, period)
         : (ProbeWorkerMonitor) workerState.get(ProbeWorkerMonitor.MONITORS);
      workerMonitors.addMonitor(new Monitor() {

         @Override
         public void run() {
            try {
               monitor(JgroupsProbeMonitorStartStage.this.run());
            } catch (IOException e) {
               log.error(e.getMessage(), e);
            }
         }

         @Override
         public void start() {
            try {
               monitor(JgroupsProbeMonitorStartStage.this.run());
            } catch (IOException e) {
               log.error(e.getMessage(), e);
            }
         }

         @Override
         public void stop() {
            try {
               monitor(JgroupsProbeMonitorStartStage.this.run());
            } catch (IOException e) {
               log.error(e.getMessage(), e);
            }
         }

         public void monitor(String[] packetsResponse) {
            for (String response : packetsResponse) {
               // TODO https://github.com/radargun/radargun/issues/553
               //workerState.getTimeline().addEvent("JgroupsProbe", new Timeline.TextEvent(TimeService.currentTimeMillis(), packet.getMessage()));
               if (printResultsAsInfo) {
                  log.info(response);
               } else {
                  log.trace(response);
               }
            }
         }
      });
      workerMonitors.start();

      return successfulResponse();
   }
}

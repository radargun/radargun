package org.radargun.stages.monitor;

import org.radargun.DistStageAck;
import org.radargun.config.Property;
import org.radargun.config.Stage;
import org.radargun.stages.AbstractJGroupsProbeStage;
import org.radargun.sysmonitor.Monitor;
import org.radargun.sysmonitor.ProbeSlaveMonitor;
import org.radargun.utils.TimeConverter;

/**
 *
 * Starts collecting probe statistics on each slave node.
 * {@link ProbeSlaveMonitor}
 *
 * @author Diego Lovison &lt;dlovison@redhat.com&gt;
 */
@Stage(name="jgroups-probe-monitor-start", doc = "Starts collecting jgroups statistics locally in each slave node.")
public class JgroupsProbeMonitorStartStage extends AbstractJGroupsProbeStage {

   @Property(doc = "Group where probe will be executed")
   private String groupName;

   @Property(doc = "Period of statistics collection. The default is 60 seconds.", converter = TimeConverter.class)
   private long period = 60_000;

   @Override
   public DistStageAck executeOnSlave() {

      if (groupName == null || (slaveState.getGroupName() != null && slaveState.getGroupName().equals(groupName))) {
         ProbeSlaveMonitor slaveMonitors = slaveState.get(ProbeSlaveMonitor.MONITORS) == null ? new ProbeSlaveMonitor(slaveState, period)
            : (ProbeSlaveMonitor) slaveState.get(ProbeSlaveMonitor.MONITORS);
         slaveMonitors.addMonitor(new Monitor() {

            @Override
            public void run() {
               JgroupsProbeMonitorStartStage.this.run();
            }

            @Override
            public void start() {
               JgroupsProbeMonitorStartStage.this.run();
            }

            @Override
            public void stop() {
               JgroupsProbeMonitorStartStage.this.run();
            }
         });
         slaveMonitors.start();
      }

      return successfulResponse();
   }
}

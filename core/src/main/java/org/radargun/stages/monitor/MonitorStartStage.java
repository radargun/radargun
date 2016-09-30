package org.radargun.stages.monitor;

import org.radargun.DistStageAck;
import org.radargun.config.Property;
import org.radargun.config.Stage;
import org.radargun.stages.AbstractDistStage;
import org.radargun.state.MasterState;
import org.radargun.sysmonitor.CpuUsageMonitor;
import org.radargun.sysmonitor.GcMonitor;
import org.radargun.sysmonitor.InternalsMonitor;
import org.radargun.sysmonitor.MasterMonitors;
import org.radargun.sysmonitor.MemoryUsageMonitor;
import org.radargun.sysmonitor.NetworkBytesMonitor;
import org.radargun.sysmonitor.OpenFilesMonitor;
import org.radargun.sysmonitor.SlaveMonitors;
import org.radargun.traits.InjectTrait;
import org.radargun.traits.InternalsExposition;
import org.radargun.traits.JmxConnectionProvider;
import org.radargun.utils.TimeConverter;

/**
 *
 * Starts collecting JVM statistics locally on master and each slave node. {@link SlaveMonitors}
 *
 * @author Alan Field &lt;afield@redhat.com&gt;
 */

@Stage(doc = "Starts collecting statistics locally on master and each slave node.", deprecatedName = "jvm-monitor-start")
public class MonitorStartStage extends AbstractDistStage {

   @Property(doc = "Specifies the network interface where statistics are gathered. "
      + "If not specified, then statistics are not collected.")
   private String interfaceName;

   @Property(doc = "Period of statistics collection. The default is 1 second.", converter = TimeConverter.class)
   private long period = 1000;

   @InjectTrait
   private JmxConnectionProvider jmxConnectionProvider;

   @InjectTrait
   private InternalsExposition internalsExposition;

   @Override
   public void initOnMaster(MasterState masterState) {
      super.initOnMaster(masterState);
      MasterMonitors masterMonitors = (MasterMonitors) masterState.get(MasterMonitors.MONITORS);
      if (masterMonitors == null) {
         masterMonitors = new MasterMonitors(masterState, period);
      }
      masterMonitors.addMonitor(new CpuUsageMonitor(jmxConnectionProvider, masterState.getTimeline()));
      masterMonitors.addMonitor(new MemoryUsageMonitor(jmxConnectionProvider, masterState.getTimeline()));
      masterMonitors.addMonitor(new GcMonitor(jmxConnectionProvider, masterState.getTimeline()));
      masterMonitors.addMonitor(new OpenFilesMonitor(jmxConnectionProvider, masterState.getTimeline()));
      if (interfaceName != null) {
         masterMonitors.addMonitor(NetworkBytesMonitor.createReceiveMonitor(interfaceName, masterState.getTimeline()));
         masterMonitors.addMonitor(NetworkBytesMonitor.createTransmitMonitor(interfaceName, masterState.getTimeline()));
      }
      masterMonitors.start();
   }

   @Override
   public DistStageAck executeOnSlave() {
      SlaveMonitors slaveMonitors = (SlaveMonitors) slaveState.get(SlaveMonitors.MONITORS);
      if (slaveMonitors == null) {
         slaveMonitors = new SlaveMonitors(slaveState, period);
      }
      slaveMonitors.addMonitor(new CpuUsageMonitor(jmxConnectionProvider, slaveState.getTimeline()));
      slaveMonitors.addMonitor(new MemoryUsageMonitor(jmxConnectionProvider, slaveState.getTimeline()));
      slaveMonitors.addMonitor(new GcMonitor(jmxConnectionProvider, slaveState.getTimeline()));
      slaveMonitors.addMonitor(new OpenFilesMonitor(jmxConnectionProvider, slaveState.getTimeline()));
      if (interfaceName != null) {
         slaveMonitors.addMonitor(NetworkBytesMonitor.createReceiveMonitor(interfaceName, slaveState.getTimeline()));
         slaveMonitors.addMonitor(NetworkBytesMonitor.createTransmitMonitor(interfaceName, slaveState.getTimeline()));
      }
      if (internalsExposition != null) {
         slaveMonitors.addMonitor(new InternalsMonitor(internalsExposition, slaveState.getTimeline()));
      }
      slaveMonitors.start();
      return successfulResponse();
   }
}

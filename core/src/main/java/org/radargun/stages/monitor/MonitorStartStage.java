package org.radargun.stages.monitor;

import org.radargun.DistStageAck;
import org.radargun.config.Property;
import org.radargun.config.Stage;
import org.radargun.reporting.Timeline;
import org.radargun.stages.AbstractDistStage;
import org.radargun.state.MasterState;
import org.radargun.sysmonitor.AbstractMonitors;
import org.radargun.sysmonitor.CpuUsageMonitor;
import org.radargun.sysmonitor.GcMonitor;
import org.radargun.sysmonitor.InternalsMonitor;
import org.radargun.sysmonitor.MasterMonitors;
import org.radargun.sysmonitor.MemoryUsageMonitor;
import org.radargun.sysmonitor.NetworkBytesMonitor;
import org.radargun.sysmonitor.OpenFilesMonitor;
import org.radargun.sysmonitor.RssMonitor;
import org.radargun.sysmonitor.SlaveMonitors;
import org.radargun.traits.InjectTrait;
import org.radargun.traits.InternalsExposition;
import org.radargun.traits.JmxConnectionProvider;
import org.radargun.utils.SystemUtils;
import org.radargun.utils.TimeConverter;

/**
 *
 * Starts collecting JVM statistics locally on master and each slave node.
 * {@link SlaveMonitors}
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
      MasterMonitors masterMonitors = masterState.get(MasterMonitors.MONITORS) == null ? new MasterMonitors(masterState, period)
            : (MasterMonitors) masterState.get(MasterMonitors.MONITORS);

      addMonitors(masterMonitors, masterState.getTimeline());
      
      masterMonitors.start();
   }

   @Override
   public DistStageAck executeOnSlave() {
      SlaveMonitors slaveMonitors = slaveState.get(SlaveMonitors.MONITORS) == null ? new SlaveMonitors(slaveState, period)
            : (SlaveMonitors) slaveState.get(SlaveMonitors.MONITORS);

      addMonitors(slaveMonitors, slaveState.getTimeline());

      if (internalsExposition != null) {
         slaveMonitors.addMonitor(new InternalsMonitor(internalsExposition, slaveState.getTimeline()));
      }

      slaveMonitors.start();
      return successfulResponse();
   }

   private void addMonitors(AbstractMonitors monitors, Timeline timeline) {
      monitors.addMonitor(new CpuUsageMonitor(jmxConnectionProvider, timeline));
      monitors.addMonitor(new MemoryUsageMonitor(jmxConnectionProvider, timeline));
      monitors.addMonitor(new GcMonitor(jmxConnectionProvider, timeline));
      monitors.addMonitor(new OpenFilesMonitor(jmxConnectionProvider, timeline));
      if (SystemUtils.IS_LINUX) {
         monitors.addMonitor(new RssMonitor(timeline));
      }
      if (interfaceName != null) {
         monitors.addMonitor(NetworkBytesMonitor.createReceiveMonitor(interfaceName, timeline));
         monitors.addMonitor(NetworkBytesMonitor.createTransmitMonitor(interfaceName, timeline));
      }

   }
}

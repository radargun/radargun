package org.radargun.stages.monitor;

import org.radargun.DistStageAck;
import org.radargun.config.Property;
import org.radargun.config.Stage;
import org.radargun.stages.AbstractDistStage;
import org.radargun.sysmonitor.CpuUsageMonitor;
import org.radargun.sysmonitor.GcMonitor;
import org.radargun.sysmonitor.InternalsMonitor;
import org.radargun.sysmonitor.MemoryUsageMonitor;
import org.radargun.sysmonitor.Monitors;
import org.radargun.sysmonitor.NetworkBytesMonitor;
import org.radargun.sysmonitor.OpenFilesMonitor;
import org.radargun.traits.InjectTrait;
import org.radargun.traits.InternalsExposition;
import org.radargun.traits.JmxConnectionProvider;
import org.radargun.utils.TimeConverter;

/**
 * 
 * Starts collecting JVM statistics locally on each slave node. {@link org.radargun.sysmonitor.Monitors}
 * 
 * @author Alan Field &lt;afield@redhat.com&gt;
 */

@Stage(doc = "Starts collecting statistics locally on each slave node.", deprecatedName = "jvm-monitor-start")
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
   public DistStageAck executeOnSlave() {
      Monitors monitor = (Monitors) slaveState.get(Monitors.MONITORS);
      if (monitor == null) {
         monitor = new Monitors(slaveState, period);
      }
      monitor.addMonitor(new CpuUsageMonitor(jmxConnectionProvider, slaveState.getTimeline()));
      monitor.addMonitor(new MemoryUsageMonitor(jmxConnectionProvider, slaveState.getTimeline()));
      monitor.addMonitor(new GcMonitor(jmxConnectionProvider, slaveState.getTimeline()));
      monitor.addMonitor(new OpenFilesMonitor(jmxConnectionProvider, slaveState.getTimeline()));
      if (interfaceName != null) {
         monitor.addMonitor(NetworkBytesMonitor.createReceiveMonitor(interfaceName, slaveState.getTimeline()));
         monitor.addMonitor(NetworkBytesMonitor.createTransmitMonitor(interfaceName, slaveState.getTimeline()));
      }
      if (internalsExposition != null) {
         monitor.addMonitor(new InternalsMonitor(internalsExposition, slaveState.getTimeline()));
      }
      monitor.start();
      return successfulResponse();
   }
}

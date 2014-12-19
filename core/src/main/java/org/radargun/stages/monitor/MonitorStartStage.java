package org.radargun.stages.monitor;

import java.util.concurrent.TimeUnit;

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

   @Property(doc = "An integer that specifies the frequency that statistics are collected. The default is one.")
   private int frequency = 1;

   @Property(doc = "Specifies the time unit that statistics are collected. "
         + "One of: MILLISECONDS, SECONDS, MINUTES, or HOURS. The default is SECONDS.")
   private TimeUnit timeUnit = TimeUnit.SECONDS;

   @InjectTrait
   private JmxConnectionProvider jmxConnectionProvider;

   @InjectTrait
   private InternalsExposition internalsExposition;

   @Override
   public DistStageAck executeOnSlave() {
      Monitors monitor = (Monitors) slaveState.get(Monitors.MONITORS);
      if (monitor == null) {
         monitor = new Monitors(slaveState, frequency, timeUnit);
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

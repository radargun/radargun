package org.radargun.stages.monitor;

import java.util.concurrent.TimeUnit;

import org.radargun.DistStageAck;
import org.radargun.config.Property;
import org.radargun.config.Stage;
import org.radargun.stages.AbstractDistStage;
import org.radargun.sysmonitor.CpuUsageMonitor;
import org.radargun.sysmonitor.GcMonitor;
import org.radargun.sysmonitor.MemoryUsageMonitor;
import org.radargun.sysmonitor.Monitors;
import org.radargun.sysmonitor.NetworkBytesMonitor;
import org.radargun.sysmonitor.OpenFilesMonitor;
import org.radargun.traits.InjectTrait;
import org.radargun.traits.JmxConnectionProvider;

/**
 * 
 * Starts collecting JVM statistics locally on each slave node. {@link org.radargun.sysmonitor.Monitors}
 * 
 * @author Alan Field &lt;afield@redhat.com&gt;
 */

@Stage(doc = "Starts collecting JVM statistics locally on each slave node.")
public class JVMMonitorStartStage extends AbstractDistStage {

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

   @Override
   public DistStageAck executeOnSlave() {
      if (slaveState.get(Monitors.MONITORS) != null) {
         log.warn("Monitors are already started");
         return successfulResponse();
      }
      Monitors monitor = new Monitors(slaveState, frequency, timeUnit);
      monitor.addMonitor(new CpuUsageMonitor(jmxConnectionProvider, slaveState.getTimeline()));
      monitor.addMonitor(new MemoryUsageMonitor(jmxConnectionProvider, slaveState.getTimeline()));
      monitor.addMonitor(new GcMonitor(jmxConnectionProvider, slaveState.getTimeline()));
      monitor.addMonitor(new OpenFilesMonitor(jmxConnectionProvider, slaveState.getTimeline()));
      if (interfaceName != null) {
         monitor.addMonitor(NetworkBytesMonitor.createReceiveMonitor(interfaceName, slaveState.getTimeline()));
         monitor.addMonitor(NetworkBytesMonitor.createTransmitMonitor(interfaceName, slaveState.getTimeline()));
      }
      monitor.start();
      return successfulResponse();
   }
}

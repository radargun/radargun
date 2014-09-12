package org.radargun.stages.monitor;

import java.util.concurrent.TimeUnit;

import org.radargun.DistStageAck;
import org.radargun.config.Property;
import org.radargun.config.Stage;
import org.radargun.stages.AbstractDistStage;
import org.radargun.sysmonitor.LocalJmxMonitor;

/**
 * 
 * Starts collecting JVM statistics locally on each slave node. {@link LocalJmxMonitor}
 * 
 * @author Alan Field &lt;afield@redhat.com&gt;
 */

@Stage(doc = "Starts collecting JVM statistics locally on each slave node.")
public class JVMMonitorStartStage extends AbstractDistStage {

   public static final String MONITOR_KEY = "JVMMonitor";

   @Property(doc = "Specifies the network interface where statistics are gathered. "
         + "If not specified, then statistics are not collected.")
   private String interfaceName;

   @Property(doc = "An integer that specifies the frequency that statistics are collected. The default is one.")
   private int frequency = 1;

   @Property(doc = "Specifies the time unit that statistics are collected. "
         + "One of: MILLISECONDS, SECONDS, MINUTES, or HOURS. The default is SECONDS.")
   private TimeUnit timeUnit = TimeUnit.SECONDS;

   @Override
   public DistStageAck executeOnSlave() {
      LocalJmxMonitor monitor = new LocalJmxMonitor(slaveState);
      monitor.setFrequency(frequency);
      monitor.setTimeUnit(timeUnit);
      monitor.setInterfaceName(interfaceName);
      monitor.startMonitoringLocal();
      slaveState.put(MONITOR_KEY, monitor);
      slaveState.addServiceListener(monitor);
      return successfulResponse();
   }
}

package org.radargun.stages.monitor;

import org.radargun.DistStageAck;
import org.radargun.config.Property;
import org.radargun.reporting.Timeline;
import org.radargun.stages.AbstractDistStage;
import org.radargun.sysmonitor.AbstractMonitors;
import org.radargun.sysmonitor.CpuUsageMonitor;
import org.radargun.sysmonitor.GcMonitor;
import org.radargun.sysmonitor.InternalsMonitor;
import org.radargun.sysmonitor.MemoryUsageMonitor;
import org.radargun.sysmonitor.NetworkBytesMonitor;
import org.radargun.sysmonitor.OpenFilesMonitor;
import org.radargun.sysmonitor.RssMonitor;
import org.radargun.sysmonitor.SystemWorkerMonitor;
import org.radargun.sysmonitor.ThreadDumpMonitor;
import org.radargun.traits.InternalsExposition;
import org.radargun.traits.JmxConnectionProvider;
import org.radargun.utils.SystemUtils;
import org.radargun.utils.TimeConverter;

/**
 *
 * Starts collecting JVM statistics locally on main and each worker node.
 * {@link SystemWorkerMonitor}
 *
 * @author Alan Field &lt;afield@redhat.com&gt;
 */
public abstract class AbstractMonitorStartStage extends AbstractDistStage {

   @Property(doc = "Specifies the network interface where statistics are gathered. "
         + "If not specified, then statistics are not collected.")
   private String interfaceName;

   @Property(doc = "Period of statistics collection. The default is 1 second.", converter = TimeConverter.class)
   protected long period = 1000;

   // related to thread dump
   @Property(doc = "Thread Dump. Default is false.")
   private boolean threadDump = false;
   @Property(doc = "Dump all locked monitors. Default is true.")
   private boolean threadDumpLockedMonitors = true;
   @Property(doc = "Dump all locked ownable synchronizers. Default is true.")
   private boolean threadDumpLockedSynchronizers = true;

   @Override
   public DistStageAck executeOnWorker() {
      SystemWorkerMonitor workerMonitors = workerState.get(SystemWorkerMonitor.MONITORS) == null ? new SystemWorkerMonitor(workerState, period)
            : (SystemWorkerMonitor) workerState.get(SystemWorkerMonitor.MONITORS);

      addMonitors(workerMonitors, workerState.getTimeline());

      if (getInternalsExposition() != null) {
         workerMonitors.addMonitor(new InternalsMonitor(getInternalsExposition(), workerState.getTimeline()));
      }

      if (threadDump) {
         workerMonitors.addMonitor(new ThreadDumpMonitor(getJmxConnectionProvider(), workerState.getTimeline(), threadDumpLockedMonitors,
               threadDumpLockedSynchronizers, this.workerState.getWorkerIndex()));
      }

      workerMonitors.start();
      return successfulResponse();
   }

   protected void addMonitors(AbstractMonitors monitors, Timeline timeline) {
      monitors.addMonitor(new CpuUsageMonitor(getJmxConnectionProvider(), timeline));
      monitors.addMonitor(new MemoryUsageMonitor(getJmxConnectionProvider(), timeline));
      monitors.addMonitor(new GcMonitor(getJmxConnectionProvider(), timeline));
      if (!SystemUtils.IS_WINDOWS) {
         monitors.addMonitor(new OpenFilesMonitor(getJmxConnectionProvider(), timeline));
      }
      if (SystemUtils.IS_LINUX) {
         monitors.addMonitor(new RssMonitor(timeline));
      }
      if (interfaceName != null) {
         monitors.addMonitor(NetworkBytesMonitor.createReceiveMonitor(interfaceName, timeline));
         monitors.addMonitor(NetworkBytesMonitor.createTransmitMonitor(interfaceName, timeline));
      }
   }

   protected abstract JmxConnectionProvider getJmxConnectionProvider();

   protected abstract InternalsExposition getInternalsExposition();
}

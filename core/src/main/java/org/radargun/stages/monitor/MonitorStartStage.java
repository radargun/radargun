package org.radargun.stages.monitor;

import org.radargun.DistStageAck;
import org.radargun.config.Property;
import org.radargun.config.Stage;
import org.radargun.reporting.Timeline;
import org.radargun.stages.AbstractDistStage;
import org.radargun.state.MainState;
import org.radargun.sysmonitor.AbstractMonitors;
import org.radargun.sysmonitor.CpuUsageMonitor;
import org.radargun.sysmonitor.GcMonitor;
import org.radargun.sysmonitor.InternalsMonitor;
import org.radargun.sysmonitor.MainMonitors;
import org.radargun.sysmonitor.MemoryUsageMonitor;
import org.radargun.sysmonitor.NetworkBytesMonitor;
import org.radargun.sysmonitor.OpenFilesMonitor;
import org.radargun.sysmonitor.RssMonitor;
import org.radargun.sysmonitor.SystemWorkerMonitor;
import org.radargun.traits.InjectTrait;
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
@Stage(doc = "Starts collecting statistics locally on main and each worker node.", deprecatedName = "jvm-monitor-start")
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
   public void initOnMain(MainState mainState) {
      super.initOnMain(mainState);
      MainMonitors mainMonitors = mainState.get(MainMonitors.MONITORS) == null ? new MainMonitors(mainState, period)
            : (MainMonitors) mainState.get(MainMonitors.MONITORS);

      addMonitors(mainMonitors, mainState.getTimeline());
      
      mainMonitors.start();
   }

   @Override
   public DistStageAck executeOnWorker() {
      SystemWorkerMonitor workerMonitors = workerState.get(SystemWorkerMonitor.MONITORS) == null ? new SystemWorkerMonitor(workerState, period)
            : (SystemWorkerMonitor) workerState.get(SystemWorkerMonitor.MONITORS);

      addMonitors(workerMonitors, workerState.getTimeline());

      if (internalsExposition != null) {
         workerMonitors.addMonitor(new InternalsMonitor(internalsExposition, workerState.getTimeline()));
      }

      workerMonitors.start();
      return successfulResponse();
   }

   private void addMonitors(AbstractMonitors monitors, Timeline timeline) {
      monitors.addMonitor(new CpuUsageMonitor(jmxConnectionProvider, timeline));
      monitors.addMonitor(new MemoryUsageMonitor(jmxConnectionProvider, timeline));
      monitors.addMonitor(new GcMonitor(jmxConnectionProvider, timeline));
      if (!SystemUtils.IS_WINDOWS) {
         monitors.addMonitor(new OpenFilesMonitor(jmxConnectionProvider, timeline));
      }
      if (SystemUtils.IS_LINUX) {
         monitors.addMonitor(new RssMonitor(timeline));
      }
      if (interfaceName != null) {
         monitors.addMonitor(NetworkBytesMonitor.createReceiveMonitor(interfaceName, timeline));
         monitors.addMonitor(NetworkBytesMonitor.createTransmitMonitor(interfaceName, timeline));
      }

   }
}

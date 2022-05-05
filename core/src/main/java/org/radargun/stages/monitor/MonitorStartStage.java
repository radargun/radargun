package org.radargun.stages.monitor;

import org.radargun.config.Stage;
import org.radargun.state.MainState;
import org.radargun.sysmonitor.MainMonitors;
import org.radargun.sysmonitor.SystemWorkerMonitor;
import org.radargun.traits.InjectTrait;
import org.radargun.traits.InternalsExposition;
import org.radargun.traits.JmxConnectionProvider;

/**
 *
 * Starts collecting JVM statistics locally on main and each worker node.
 * {@link SystemWorkerMonitor}
 *
 * @author Alan Field &lt;afield@redhat.com&gt;
 */
@Stage(doc = "Starts collecting statistics locally on main and each worker node.", deprecatedName = "jvm-monitor-start")
public class MonitorStartStage extends AbstractMonitorStartStage {

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
   protected JmxConnectionProvider getJmxConnectionProvider() {
      return this.jmxConnectionProvider;
   }

   @Override
   protected InternalsExposition getInternalsExposition() {
      return this.internalsExposition;
   }
}

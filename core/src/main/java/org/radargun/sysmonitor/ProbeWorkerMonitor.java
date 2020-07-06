package org.radargun.sysmonitor;

import org.radargun.state.ServiceListener;
import org.radargun.state.WorkerState;
import org.radargun.traits.Lifecycle;

/**
 * Retrieves probe statistics from worker nodes.
 *
 * @author Diego Lovison &lt;dlovison@redhat.com&gt;
 */
public class ProbeWorkerMonitor extends AbstractMonitors<WorkerState, ServiceListener> implements ServiceListener {
   public static final String MONITORS = ProbeWorkerMonitor.class.getName();

   public ProbeWorkerMonitor(WorkerState state, long period) {
      super(state, period);
   }

   @Override
   public synchronized void start() {
      state.addListener(this);
      state.put(MONITORS, this);
      Lifecycle lifecycle = state.getTrait(Lifecycle.class);
      if (lifecycle != null && lifecycle.isRunning()) {
         startInternal();
      }
   }

   @Override
   public void afterServiceStart() {
      startInternal();
   }

   @Override
   public void beforeServiceStop(boolean graceful) {
      stopInternal();
   }

   @Override
   public void serviceDestroyed() {
      stop();
   }

   public String getName() {
      return MONITORS;
   }
}

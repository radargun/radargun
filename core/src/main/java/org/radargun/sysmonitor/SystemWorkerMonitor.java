package org.radargun.sysmonitor;

import org.radargun.state.ServiceListener;
import org.radargun.state.WorkerState;
import org.radargun.traits.Lifecycle;

/**
 * Retrieves system statistics from worker nodes.
 */
public class SystemWorkerMonitor extends AbstractMonitors<WorkerState, ServiceListener> implements ServiceListener {
   public static final String MONITORS = SystemWorkerMonitor.class.getName();

   public SystemWorkerMonitor(WorkerState state, long period) {
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

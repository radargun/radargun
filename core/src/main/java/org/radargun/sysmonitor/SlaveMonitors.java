package org.radargun.sysmonitor;

import org.radargun.state.ServiceListener;
import org.radargun.state.SlaveState;
import org.radargun.traits.Lifecycle;

/**
 * Retrieves JMX statistics from slave nodes.
 */
public class SlaveMonitors extends AbstractMonitors<SlaveState, ServiceListener> implements ServiceListener {
   public static final String MONITORS = SlaveMonitors.class.getName();

   public SlaveMonitors(SlaveState state, long period) {
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

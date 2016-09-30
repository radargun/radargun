package org.radargun.sysmonitor;

import org.radargun.state.ServiceListener;
import org.radargun.state.SlaveState;
import org.radargun.traits.Lifecycle;

/**
 * Retrieves JMX statistics from slave nodes.
 */
public class SlaveMonitors extends AbstractMonitors implements ServiceListener {
   public static final String MONITORS = SlaveMonitors.class.getName();
   private final SlaveState slaveState;

   public SlaveMonitors(SlaveState slaveState, long period) {
      super(period);
      this.slaveState = slaveState;
   }

   public synchronized void start() {
      slaveState.put(MONITORS, this);
      slaveState.addServiceListener(this);
      Lifecycle lifecycle = slaveState.getTrait(Lifecycle.class);
      if (lifecycle != null && lifecycle.isRunning()) {
         startInternal();
      }
   }

   public synchronized void stop() {
      slaveState.removeServiceListener(this);
      slaveState.remove(MONITORS);
      stopInternal();
   }

   @Override
   public void beforeServiceStart() {
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
   public void afterServiceStop(boolean graceful) {
   }

   @Override
   public void serviceDestroyed() {
      stop();
   }
}

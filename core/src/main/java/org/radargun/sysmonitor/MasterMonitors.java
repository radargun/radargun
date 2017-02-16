package org.radargun.sysmonitor;

import org.radargun.state.MasterListener;
import org.radargun.state.MasterState;

/**
 * Retrieves JMX statistics on master node.
 */
public class MasterMonitors extends AbstractMonitors implements MasterListener {
   public static final String MONITORS = MasterMonitors.class.getName();
   private final MasterState masterState;

   public MasterMonitors(MasterState masterState, long period) {
      super(period);
      this.masterState = masterState;
   }

   public synchronized void start() {
      masterState.put(MONITORS, this);
      masterState.addListener(this);
      startInternal();
   }

   public synchronized void stop() {
      masterState.remove(MONITORS);
      masterState.removeListener(this);
      stopInternal();
   }

   public void afterConfiguration() {
      MasterListener.super.afterConfiguration();
      stop();
   }
}

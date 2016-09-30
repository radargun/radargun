package org.radargun.sysmonitor;

import org.radargun.state.MasterState;

/**
 * Retrieves JMX statistics on master node.
 */
public class MasterMonitors extends AbstractMonitors {
   public static final String MONITORS = MasterMonitors.class.getName();
   private final MasterState masterState;

   public MasterMonitors(MasterState masterState, long period) {
      super(period);
      this.masterState = masterState;
   }

   public synchronized void start() {
      masterState.put(MONITORS, this);
      startInternal();
   }

   public synchronized void stop() {
      masterState.remove(MONITORS);
      stopInternal();
   }
}

package org.radargun.sysmonitor;

import org.radargun.state.MasterListener;
import org.radargun.state.MasterState;

/**
 * Retrieves JMX statistics on master node.
 */
public class MasterMonitors extends AbstractMonitors<MasterState, MasterListener> implements MasterListener{
   public static final String MONITORS = MasterMonitors.class.getName();

   public MasterMonitors(MasterState masterState, long period) {
      super(masterState, period);
   }

   public void afterConfiguration() {
      stop();
   }
   
   public String getName(){
      return MONITORS;
   }
}

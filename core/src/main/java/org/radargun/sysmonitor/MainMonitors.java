package org.radargun.sysmonitor;

import org.radargun.state.MainListener;
import org.radargun.state.MainState;

/**
 * Retrieves JMX statistics on main node.
 */
public class MainMonitors extends AbstractMonitors<MainState, MainListener> implements MainListener{
   public static final String MONITORS = MainMonitors.class.getName();

   public MainMonitors(MainState mainState, long period) {
      super(mainState, period);
   }

   public void afterConfiguration() {
      stop();
   }
   
   public String getName(){
      return MONITORS;
   }
}

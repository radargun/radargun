package org.radargun.stressors;

import org.radargun.CacheWrapperStressor;
import org.radargun.config.Property;
import org.radargun.config.Stressor;
import org.radargun.state.SlaveState;

/**
 * @author Mircea Markus <mircea.markus@gmail.com>
 */
@Stressor(doc = "Ancestor of all common stressors")
public abstract class AbstractCacheWrapperStressor implements CacheWrapperStressor {

   @Property(doc = "Should be the JVM monitored during this stressor run? Default is false.")
   private boolean sysMonitorEnabled = false;

   //The object which will store the state of the stressors.
   protected SlaveState slaveState;

   @Override
   public boolean isSysMonitorEnabled() {
      return sysMonitorEnabled;
   }

   public void setSlaveState(SlaveState slaveState) {
      this.slaveState = slaveState;
   }
}

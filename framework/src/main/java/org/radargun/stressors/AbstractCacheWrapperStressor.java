package org.radargun.stressors;

import org.radargun.CacheWrapperStressor;

/**
 * @author Mircea Markus <mircea.markus@gmail.com>
 */
public abstract class AbstractCacheWrapperStressor implements CacheWrapperStressor {

   private boolean sysMonitorEnabled = false;

   @Override
   public void setSysMonitorEnabled(boolean enabled) {
      sysMonitorEnabled = enabled;
   }

   @Override
   public boolean isSysMonitorEnabled() {
      return sysMonitorEnabled;
   }
}

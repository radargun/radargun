package org.radargun.stressors;

import org.radargun.CacheWrapperStressor;
import org.radargun.config.Property;
import org.radargun.config.Stressor;

/**
 * @author Mircea Markus <mircea.markus@gmail.com>
 */
@Stressor(doc = "Ancestor of all common stressors")
public abstract class AbstractCacheWrapperStressor implements CacheWrapperStressor {

   @Property(doc = "Should be the JVM monitored during this stressor run? Default is false.")
   private boolean sysMonitorEnabled = false;

   @Override
   public boolean isSysMonitorEnabled() {
      return sysMonitorEnabled;
   }
}

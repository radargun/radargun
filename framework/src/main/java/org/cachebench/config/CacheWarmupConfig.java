package org.cachebench.config;

import java.util.Map;
import java.util.HashMap;

/**
 * @author Mircea.Markus@jboss.com
 */
public class CacheWarmupConfig extends GenericParamsConfig
{
   private String warmupClass;

   public String getWarmupClass()
   {
      return warmupClass;
   }

   public void setWarmupClass(String warmupClass)
   {
      this.warmupClass = warmupClass;
   }
}

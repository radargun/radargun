package org.cachebench.warmup;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.cachebench.CacheWrapper;

import java.util.HashMap;
import java.util.Map;

/**
 * Warmups the cache.
 *
 * @author Mircea.Markus@jboss.com
 * @since 2.2
 */
public abstract class CacheWarmup
{

   private static final Log log = LogFactory.getLog(CacheWarmup.class);

   private Map<String, String> configParams = new HashMap<String, String>();

   public void setConfigParams(Map<String, String> configParams)
   {
      this.configParams = configParams;
   }

   public String getConfigParam(String name)
   {
      return configParams.get(name);
   }

   /**
    * Calls {@link #performWarmupOperations(CacheWrapper)} amd clears the cache.
    */
   public final void warmup(CacheWrapper cacheWrapper)
   {
      long startTime = System.currentTimeMillis();
      try
      {
         performWarmupOperations(cacheWrapper);
      } catch (Exception e)
      {
         log.warn("Received exception durring cache warmup" + e.getMessage());
      }
      log.info("The warmup took: " + ((System.currentTimeMillis() - startTime) / 1000) + " seconds." );
      try
      {
         cacheWrapper.empty();
      } catch (Exception e)
      {
         log.warn("Received exception durring cache warmup", e);
      }
   }

   public abstract void performWarmupOperations(CacheWrapper wrapper) throws Exception;
}

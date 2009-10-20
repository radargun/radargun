package org.cachebench.warmup;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.cachebench.CacheWrapper;

import java.util.Arrays;
import java.util.List;

/**
 * Perfoms N puts, gets and removals, where n is configurable.
 *
 * @author Mircea.Markus@jboss.com
 * @since 2.2
 */
public class PutGetCacheWarmup extends CacheWarmup
{
   Log log = LogFactory.getLog(PutGetCacheWarmup.class);

   public void performWarmupOperations(CacheWrapper wrapper) throws Exception
   {
      Integer opCount = Integer.parseInt(getConfigParam("operationCount"));
      log.info("Cache launched, performing " + opCount + " put and get operations ");
      List<String> path = Arrays.asList("a", "b", "c");
      for (int i = 0; i < opCount; i++)
      {
         try
         {
            wrapper.put(path, String.valueOf(opCount), String.valueOf(opCount));
         }
         catch (Throwable e)
         {
            log.trace("Exception on cache warmup", e);
         }
      }

      for (int i = 0; i < opCount; i++)
      {
         wrapper.get(path, String.valueOf(opCount));
      }
      log.trace("Cache warmup ended!");
   }
}

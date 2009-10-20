package org.cachebench.warmup;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.cachebench.CacheWrapper;

/**
 * Does not warmup the cache.
 *
 * @author Mircea.Markus@jboss.com
 */
public class NoCacheWarmup extends CacheWarmup
{
   Log log = LogFactory.getFactory().getInstance(NoCacheWarmup.class);
   public void performWarmupOperations(CacheWrapper wrapper) throws Exception
   {
      log.info("Using no cache warmup");      
   }
}

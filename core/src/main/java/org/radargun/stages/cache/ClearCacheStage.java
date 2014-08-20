package org.radargun.stages.cache;

import java.util.Random;

import org.radargun.DistStageAck;
import org.radargun.config.Property;
import org.radargun.config.Stage;
import org.radargun.stages.AbstractDistStage;
import org.radargun.stages.cache.background.BackgroundOpsManager;
import org.radargun.traits.BasicOperations;
import org.radargun.traits.CacheInformation;
import org.radargun.traits.InjectTrait;
import org.radargun.traits.LocalBasicOperations;
import org.radargun.traits.Transactional;
import org.radargun.utils.Utils;

/**
 * Distributed stage that will clear the content of the cache wrapper on each slave.
 *
 * @author Mircea Markus &lt;Mircea.Markus@jboss.com&gt;
 */
@Stage(doc = "Removes all data from the cache")
public class ClearCacheStage extends AbstractDistStage {

   @Property(doc = "Execute local variant of clear on each slave. Default is null - local clear is performed, only if it is provided by the service." +
         " True enforces local clear - if given service does not provide the feature, exception is thrown.")
   private Boolean local = null;

   @Property(doc = "Execute the clear inside explicit transaction.")
   private boolean useTransaction = false;

   @Property(doc = "Name of the cache to be cleared. Default is the default cache.")
   private String cacheName;

   @InjectTrait(dependency = InjectTrait.Dependency.MANDATORY)
   private BasicOperations basicOperations;

   @InjectTrait
   private LocalBasicOperations localBasicOperations;

   @InjectTrait
   private Transactional transactional;

   @InjectTrait
   private CacheInformation cacheInformation;

   public DistStageAck executeOnSlave() {
      BackgroundOpsManager.beforeCacheClear(slaveState);
      if (!isServiceRunnning()) {
         log.info("This slave is dead, cannot clear cache.");
         return successfulResponse();
      }
      if (Boolean.TRUE.equals(local) && localBasicOperations == null) {
         return errorResponse("This cache does not support local clear", null);
      }
      if (useTransaction && transactional == null) {
         return errorResponse("This cache does not support transactions", null);
      }
      for (int i = 0; i < 5; i++) {
         try {
            log.info(Utils.printMemoryFootprint(true));
            if (shouldExecute()) {
               DistStageAck response = executeClear();
               if (response != null) return response;
               if (cacheInformation == null) return successfulResponse();
            } else {
               if (Boolean.FALSE.equals(local) || localBasicOperations == null) {
                  int size;
                  for (int count = new Random().nextInt(20) + 10; count > 0 && (size = cacheInformation.getCache(cacheName).getLocalSize()) > 0; --count) {
                     log.debug("Waiting until the cache gets empty (contains " + size + " entries)");
                     Thread.sleep(1000);
                  }
                  if ((size = cacheInformation.getCache(cacheName).getLocalSize()) > 0) {
                     log.error("The cache was not cleared from another node (contains " + size + " entries), clearing locally");
                     DistStageAck response = executeClear();
                     if (response != null) return response;
                     if (cacheInformation == null) return successfulResponse();
                  }
               }
            }
            return successfulResponse();
         } catch (Exception e) {
            log.warn("Failed to clear cache(s)", e);
         } finally {
            System.gc();
            log.info(Utils.printMemoryFootprint(false));
         }
      }
      return errorResponse("Failed to clear the cache.", null);
   }

   protected DistStageAck executeClear() {
      BasicOperations.Cache cache;
      if (!Boolean.FALSE.equals(local) && localBasicOperations != null) {
         cache = localBasicOperations.getLocalCache(cacheName);
      } else {
         cache = basicOperations.getCache(cacheName);
      }
      if (cache == null) {
         return errorResponse("There is no cache '" + cacheName + "'", null);
      }
      Transactional.Resource txCache = null;
      try {
         if (useTransaction) {
            txCache = transactional.getResource(cacheName);
            if (txCache == null) {
               return errorResponse("No transactions for '" + cacheName + "'", null);
            }
            txCache.startTransaction();
         }
         cache.clear();
         if (useTransaction) {
            txCache.endTransaction(true);
         }
      } catch (RuntimeException e) {
         if (txCache != null) {
            try {
               txCache.endTransaction(false);
            } catch (Exception e2) {
               log.error("Cannot roll back", e2);
            }
         }
         throw e;
      }
      return null;
   }
}

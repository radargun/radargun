package org.radargun.service;

import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;

import org.infinispan.AdvancedCache;
import org.infinispan.distexec.DefaultExecutorService;
import org.infinispan.distexec.DistributedExecutorService;
import org.infinispan.distexec.DistributedTaskBuilder;
import org.radargun.logging.Log;
import org.radargun.logging.LogFactory;

/**
 * DefaultExecutorService was removed but Infinispan90CacheInfo inherit not directly from Infinispan52CacheInfo
 * When creating a new Cache it will threw NoClassDefFoundError exception.
 * With a separated class it won't occurs because the getTotalSize was overwritten in Infinispan90CacheInfo
 */
public class Infinispan52CacheInfoTotalSize {

   private static final Log log = LogFactory.getLog(Infinispan52CacheInfoTotalSize.class);

   private Infinispan52CacheInfoTotalSize() {

   }

   public static long getTotalSize(AdvancedCache cache) {
      long totalSize = 0;
      DistributedExecutorService des = new DefaultExecutorService(cache);
      CacheSizer<?, ?, Integer> cacheSizer = new CacheSizer<Object, Object, Integer>();
      DistributedTaskBuilder<Integer> taskBuilder = des.createDistributedTaskBuilder(cacheSizer);
      List<Future<Integer>> futureList = des.submitEverywhere(taskBuilder.build());

      for (Future<Integer> future : futureList) {
         try {
            totalSize += future.get().intValue();
         } catch (InterruptedException e) {
            log.error("The distributed task was interrupted.", e);
            return -1;
         } catch (ExecutionException e) {
            log.error("An error occurred executing the distributed task.", e);
            return -1;
         }
      }

      if (cache.getAdvancedCache().getDistributionManager() != null) {
         int numMembers = cache.getCacheManager().getMembers().size();
         int numOwners = cache.getAdvancedCache().getCacheConfiguration().clustering().hash().numOwners();
         // In replicated mode, numOwners is always 2
         if (cache.getAdvancedCache().getCacheConfiguration().clustering().cacheMode().isReplicated()) {
            numOwners = numMembers;
         }
         // Adjust for current cluster size
         if (numMembers >= numOwners) {
            totalSize /= numOwners;
         } else {
            totalSize /= numMembers;
         }
      }

      return totalSize;
   }
}

package org.radargun.service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;

import org.infinispan.AdvancedCache;
import org.infinispan.container.entries.InternalCacheEntry;
import org.infinispan.distexec.DefaultExecutorService;
import org.infinispan.distexec.DistributedExecutorService;
import org.infinispan.distexec.DistributedTaskBuilder;
import org.infinispan.distribution.DistributionManager;
import org.infinispan.distribution.ch.ConsistentHash;
import org.radargun.logging.Log;
import org.radargun.logging.LogFactory;
import org.radargun.traits.CacheInformation;

/**
 * @author Radim Vansa &lt;rvansa@redhat.com&gt;
 */
public class Infinispan52CacheInfo extends InfinispanCacheInfo {
   private final Log log = LogFactory.getLog(this.getClass());

   public Infinispan52CacheInfo(InfinispanEmbeddedService service) {
      super(service);
   }

   @Override
   public CacheInformation.Cache getCache(String cacheName) {
      return new Cache(service.getCache(cacheName).getAdvancedCache());
   }

   protected class Cache extends InfinispanCacheInfo.Cache {
      public Cache(AdvancedCache cache) {
         super(cache);
      }

      @Override
      public Map<?, Long> getStructuredSize() {
         ConsistentHash ch = ((DistributionManager) cache.getDistributionManager()).getReadConsistentHash();
         int[] segmentSizes = new int[ch.getNumSegments()];
         for (InternalCacheEntry entry : cache.getDataContainer()) {
            segmentSizes[ch.getSegment(entry.getKey())]++;
         }
         Map<Integer, Long> structured = new HashMap<>();
         for (int i = 0; i < segmentSizes.length; ++i) {
            structured.put(i, (long) segmentSizes[i]);
         }
         return structured;
      }

      @Override
      public int getEntryOverhead() {
         return 152;
      }

      @Override
      public long getTotalSize() {
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
}

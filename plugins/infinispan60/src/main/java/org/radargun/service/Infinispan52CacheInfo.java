package org.radargun.service;

import java.util.HashMap;
import java.util.Map;

import org.infinispan.AdvancedCache;
import org.infinispan.container.entries.InternalCacheEntry;
import org.infinispan.distribution.ch.ConsistentHash;
import org.radargun.traits.CacheInformation;

/**
 * @author Radim Vansa &lt;rvansa@redhat.com&gt;
 */
public class Infinispan52CacheInfo extends InfinispanCacheInfo {

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
         ConsistentHash ch = cache.getDistributionManager().getReadConsistentHash();
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
         return Infinispan52CacheInfoTotalSize.getTotalSize(cache);
      }
   }
}

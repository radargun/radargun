package org.radargun.service;

import java.util.HashMap;
import java.util.Map;

import org.infinispan.AdvancedCache;
import org.infinispan.container.entries.InternalCacheEntry;
import org.infinispan.distribution.DistributionManager;
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
      public Map<?, Integer> getStructuredSize() {
         ConsistentHash ch = ((DistributionManager) cache.getDistributionManager()).getReadConsistentHash();
         int segmentSizes[] = new int[ch.getNumSegments()];
         for (InternalCacheEntry entry : cache.getDataContainer()) {
            segmentSizes[ch.getSegment(entry.getKey())]++;
         }
         Map<Integer, Integer> structured = new HashMap<Integer, Integer>();
         for (int i = 0; i < segmentSizes.length; ++i) {
            structured.put(i, segmentSizes[i]);
         }
         return structured;
      }

      @Override
      public int getEntryOverhead() {
         return 152;
      }
   }
}

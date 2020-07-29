package org.radargun.service;

import java.util.HashMap;
import java.util.Map;

import org.infinispan.AdvancedCache;
import org.infinispan.container.DataContainer;
import org.infinispan.container.entries.CacheEntry;
import org.infinispan.distribution.LocalizedCacheTopology;
import org.infinispan.distribution.ch.ConsistentHash;
import org.radargun.traits.CacheInformation;

public class Infinispan110CacheInfo extends Infinispan90CacheInfo {

   public Infinispan110CacheInfo(InfinispanEmbeddedService service) {
      super(service);
   }

   @Override
   public CacheInformation.Cache getCache(String cacheName) {
      return new Infinispan110CacheInfo.Cache(service.getCache(cacheName).getAdvancedCache());
   }

   protected class Cache extends Infinispan90CacheInfo.Cache {
      public Cache(AdvancedCache cache) {
         super(cache);
      }

      @Override
      public Map<?, Long> getStructuredSize() {
         ConsistentHash ch = cache.getDistributionManager().getReadConsistentHash();
         int[] segmentSizes = new int[ch.getNumSegments()];
         DataContainer dataContainer = cache.getDataContainer();
         LocalizedCacheTopology localizedCacheTopology = cache.getDistributionManager().getCacheTopology();
         dataContainer.forEach(entry -> {
            if (entry instanceof CacheEntry) {
               CacheEntry cacheEntry = (CacheEntry) entry;
               int segment = localizedCacheTopology.getSegment(cacheEntry.getKey());
               segmentSizes[segment]++;
            } else {
               throw new IllegalStateException("Entry should be a CacheEntry");
            }
         });
         Map<Integer, Long> structured = new HashMap<>();
         for (int i = 0; i < segmentSizes.length; ++i) {
            structured.put(i, (long) segmentSizes[i]);
         }
         return structured;
      }

   }
}

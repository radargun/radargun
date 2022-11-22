package org.radargun.service;

import java.util.HashMap;
import java.util.Map;

import org.infinispan.AdvancedCache;
import org.infinispan.container.DataContainer;
import org.infinispan.distribution.ch.ConsistentHash;
import org.radargun.traits.CacheInformation;

/**
 * @author Radim Vansa &lt;rvansa@redhat.com&gt;
 * @author Alan Field &lt;afield@redhat.com&gt;
 */
public abstract class Infinispan70CacheInfo extends Infinispan53CacheInfo {

   public Infinispan70CacheInfo(InfinispanEmbeddedService service) {
      super(service);
   }

   public abstract CacheInformation.Cache getCache(String cacheName);

   protected abstract class Cache extends Infinispan53CacheInfo.Cache {
      public Cache(AdvancedCache cache) {
         super(cache);
      }

      @Override
      public Map<?, Long> getStructuredSize() {
         ConsistentHash ch = cache.getDistributionManager().getReadConsistentHash();
         int[] segmentSizes = new int[ch.getNumSegments()];
         DataContainer dataContainer = cache.getDataContainer();
         dataContainer.entrySet().forEach(entry -> {
            segmentSizes[ch.getSegment(entry)]++;
         });
         Map<Integer, Long> structured = new HashMap<>();
         for (int i = 0; i < segmentSizes.length; ++i) {
            structured.put(i, (long) segmentSizes[i]);
         }
         return structured;
      }

      @Override
      public abstract long getTotalSize();
   }

}

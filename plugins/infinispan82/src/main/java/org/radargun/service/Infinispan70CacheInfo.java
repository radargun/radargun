package org.radargun.service;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

import org.infinispan.AdvancedCache;
import org.infinispan.commons.util.CloseableIterable;
import org.infinispan.container.DataContainer;
import org.infinispan.distribution.ch.ConsistentHash;
import org.infinispan.iteration.EntryIterable;
import org.radargun.filters.AllFilter;
import org.radargun.filters.NullConverter;
import org.radargun.logging.Log;
import org.radargun.logging.LogFactory;
import org.radargun.traits.CacheInformation;

/**
 * @author Radim Vansa &lt;rvansa@redhat.com&gt;
 * @author Alan Field &lt;afield@redhat.com&gt;
 */
public class Infinispan70CacheInfo extends Infinispan53CacheInfo {
   private final Log log = LogFactory.getLog(Infinispan70CacheInfo.class);

   public Infinispan70CacheInfo(InfinispanEmbeddedService service) {
      super(service);
   }

   @Override
   public CacheInformation.Cache getCache(String cacheName) {
      return new Cache(service.getCache(cacheName).getAdvancedCache());
   }

   protected class Cache extends Infinispan53CacheInfo.Cache {
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
      public long getTotalSize() {
         long totalSize = 0;
         EntryIterable entryIterator = null;
         try {
            entryIterator = cache.filterEntries(AllFilter.INSTANCE);
            CloseableIterable ci = entryIterator.converter(NullConverter.INSTANCE);
            Iterator iter = ci.iterator();
            while (iter.hasNext()) {
               iter.next();
               totalSize++;
            }
            return totalSize;
         } finally {
            if (entryIterator != null) {
               try {
                  entryIterator.close();
               } catch (Exception e) {
                  log.error("Failed to close EntryIterable", e);
               }
            }
         }
      }

   }

}

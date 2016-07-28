package org.radargun.service;

import java.util.Iterator;

import org.infinispan.AdvancedCache;
import org.infinispan.commons.util.CloseableIterable;
import org.infinispan.iteration.EntryIterable;
import org.radargun.filters.AllFilter;
import org.radargun.filters.NullConverter;
import org.radargun.logging.Log;
import org.radargun.logging.LogFactory;
import org.radargun.traits.CacheInformation;

/**
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

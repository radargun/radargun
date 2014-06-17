package org.radargun.service;

import java.io.IOException;
import java.io.Serializable;
import java.util.Iterator;

import org.infinispan.AdvancedCache;
import org.infinispan.commons.util.CloseableIterable;
import org.infinispan.filter.Converter;
import org.infinispan.filter.KeyValueFilter;
import org.infinispan.iteration.EntryIterable;
import org.infinispan.metadata.Metadata;
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
      public int getTotalSize() {
         int totalSize = 0;
         EntryIterable entryIterator = null;
         try {
            entryIterator = cache.filterEntries(new AllEntriesFilter());
            CloseableIterable ci = entryIterator.converter(new AllEntriesConverter());
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

   protected class AllEntriesFilter implements KeyValueFilter, Serializable {

      @Override
      public boolean accept(Object key, Object value, Metadata metadata) {
         return true;
      }

   }
   
   protected class AllEntriesConverter implements Converter {

      @Override
      public Object convert(Object key, Object value, Metadata metadata) {
         return 0;
      }
      
   }
}

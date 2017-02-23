package org.radargun.service;

import org.infinispan.AdvancedCache;
import org.radargun.logging.Log;
import org.radargun.logging.LogFactory;
import org.radargun.traits.CacheInformation;

/**
 * @author Alan Field &lt;afield@redhat.com&gt;
 */
public class Infinispan90CacheInfo extends Infinispan70CacheInfo {
   private final Log log = LogFactory.getLog(Infinispan90CacheInfo.class);

   public Infinispan90CacheInfo(InfinispanEmbeddedService service) {
      super(service);
   }

   @Override
   public CacheInformation.Cache getCache(String cacheName) {
      return new Cache(service.getCache(cacheName).getAdvancedCache());
   }

   protected class Cache extends Infinispan70CacheInfo.Cache {
      public Cache(AdvancedCache cache) {
         super(cache);
      }

      @Override
      public long getTotalSize() {
         return cache.keySet().stream().count();
      }

   }

}

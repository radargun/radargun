package org.radargun.service;

import java.util.Set;

import org.infinispan.AdvancedCache;
import org.radargun.traits.CacheInformation;

/**
 * @author Radim Vansa &lt;rvansa@redhat.com&gt;
 */
public class InfinispanCacheInfo implements CacheInformation {
   protected final InfinispanEmbeddedService service;

   public InfinispanCacheInfo(InfinispanEmbeddedService service) {
      this.service = service;
   }

   @Override
   public CacheInformation.Cache getCache(String cacheName) {
      return new Cache(service.getCache(cacheName).getAdvancedCache());
   }

   @Override
   public String getDefaultCacheName() {
      return service.cacheName;
   }

   @Override
   public Set<String> getCacheNames() {
      return service.cacheManager.getCacheNames();
   }

   protected class Cache implements CacheInformation.Cache {
      AdvancedCache cache;

      public Cache(AdvancedCache cache) {
         this.cache = cache;
      }

      @Override
      public int getLocalSize() {
         return cache.size();
      }

      @Override
      public int getTotalSize() {
         return -1;
      }

      @Override
      public int getNumReplicas() {
         return service.getNumOwners(cache);
      }

      @Override
      public int getEntryOverhead() {
         return -1;
      }
   }
}

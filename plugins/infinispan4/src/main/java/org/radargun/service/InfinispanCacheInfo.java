package org.radargun.service;

import java.util.Collections;
import java.util.Map;
import java.util.Set;

import org.infinispan.AdvancedCache;
import org.infinispan.context.Flag;
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
      protected AdvancedCache cache;

      public Cache(AdvancedCache cache) {
         this.cache = cache;
      }

      @Override
      public long getOwnedSize() {
         return -1;
      }

      @Override
      public long getLocallyStoredSize() {
         return cache.withFlags(Flag.CACHE_MODE_LOCAL).size();
      }

      @Override
      public long getMemoryStoredSize() {
         return cache.withFlags(Flag.SKIP_CACHE_LOAD, Flag.CACHE_MODE_LOCAL).size();
      }

      @Override
      public long getTotalSize() {
         return -1;
      }

      @Override
      public Map<?, Long> getStructuredSize() {
         return Collections.singletonMap(cache.getName(), getOwnedSize());
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

package org.radargun.service;

import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Map;

import net.sf.ehcache.Ehcache;
import org.radargun.traits.CacheInformation;

/**
 * @author Radim Vansa &lt;rvansa@redhat.com&gt;
 */
public class EHCacheInfo implements CacheInformation {
   protected final EHCacheService service;

   public EHCacheInfo(EHCacheService service) {
      this.service = service;
   }

   @Override
   public String getDefaultCacheName() {
      return service.cacheName;
   }

   @Override
   public Collection<String> getCacheNames() {
      return Arrays.asList(service.manager.getCacheNames());
   }

   @Override
   public Cache getCache(String cacheName) {
      return new Cache(service.getCache(cacheName));
   }

   protected class Cache implements CacheInformation.Cache {
      private final Ehcache cache;

      public Cache(Ehcache cache) {
         this.cache = cache;
      }

      @Override
      public long getOwnedSize() {
         return cache.getSize();
      }

      @Override
      public long getLocallyStoredSize() {
         return cache.getSize();
      }

      @Override
      public long getMemoryStoredSize() {
         return cache.getSize();
      }

      @Override
      public long getTotalSize() {
         return -1;
      }

      @Override
      public Map<?, Long> getStructuredSize() {
         return Collections.singletonMap(cache.getName(), getLocallyStoredSize());
      }

      @Override
      public int getNumReplicas() {
         return -1;
      }

      @Override
      public int getEntryOverhead() {
         return -1;
      }

   }
}

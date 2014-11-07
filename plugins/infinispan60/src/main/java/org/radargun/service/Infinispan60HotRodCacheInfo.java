package org.radargun.service;


import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

import org.infinispan.client.hotrod.RemoteCache;
import org.radargun.traits.CacheInformation;

/**
 * @author Matej Cimbora &lt;mcimbora@redhat.com&gt;
 */
public class Infinispan60HotRodCacheInfo implements CacheInformation {

   private Infinispan60HotrodService service;

   public Infinispan60HotRodCacheInfo(Infinispan60HotrodService service) {
      this.service = service;
   }

   @Override
   public String getDefaultCacheName() {
      return service.cacheName;
   }

   @Override
   public Collection<String> getCacheNames() {
      return Arrays.asList(service.cacheName);
   }

   @Override
   public Cache getCache(String cacheName) {
      if (cacheName == null) {
         RemoteCache cache = service.managerForceReturn.getCache();
         return new Cache(service.managerForceReturn.getCache(true));
      } else {
         return new Cache(service.managerForceReturn.getCache(cacheName, true));
      }
   }

   protected class Cache implements CacheInformation.Cache {
      protected RemoteCache cache;

      public Cache(RemoteCache cache) {
         this.cache = cache;
      }

      @Override
      public long getOwnedSize() {
         return -1;
      }

      @Override
      public long getLocallyStoredSize() {
         return -1;
      }

      @Override
      public long getMemoryStoredSize() {
         return -1;
      }

      @Override
      public long getTotalSize() {
         return cache.size();
      }

      @Override
      public Map<?, Long> getStructuredSize() {
         return new HashMap<>();
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

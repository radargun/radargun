package org.radargun.service;

import org.infinispan.AdvancedCache;
import org.radargun.traits.CacheInformation;

public class Infinispan53CacheInfo extends Infinispan52CacheInfo {
   public Infinispan53CacheInfo(InfinispanEmbeddedService service) {
      super(service);
   }

   @Override
   public CacheInformation.Cache getCache(String cacheName) {
      return new Cache(service.getCache(cacheName).getAdvancedCache());
   }

   protected class Cache extends Infinispan52CacheInfo.Cache {
      public Cache(AdvancedCache cache) {
         super(cache);
      }

      @Override
      public int getEntryOverhead() {
         return 136;
      }
   }
}

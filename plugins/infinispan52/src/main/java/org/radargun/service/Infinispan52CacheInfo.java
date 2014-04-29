package org.radargun.service;

import org.infinispan.AdvancedCache;
import org.radargun.traits.CacheInformation;

/**
 * @author Radim Vansa &lt;rvansa@redhat.com&gt;
 */
public class Infinispan52CacheInfo extends InfinispanCacheInfo {
   public Infinispan52CacheInfo(InfinispanEmbeddedService service) {
      super(service);
   }

   @Override
   public CacheInformation.Cache getCache(String cacheName) {
      return new Cache(service.getCache(cacheName).getAdvancedCache());
   }

   protected class Cache extends InfinispanCacheInfo.Cache {
      public Cache(AdvancedCache cache) {
         super(cache);
      }

      @Override
      public int getEntryOverhead() {
         return 152;
      }
   }
}

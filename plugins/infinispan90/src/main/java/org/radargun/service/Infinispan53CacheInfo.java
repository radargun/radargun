package org.radargun.service;

import org.infinispan.AdvancedCache;
import org.radargun.traits.CacheInformation;

/**
 * @author Radim Vansa &lt;rvansa@redhat.com&gt;
 */
public abstract class Infinispan53CacheInfo extends Infinispan52CacheInfo {
   public Infinispan53CacheInfo(InfinispanEmbeddedService service) {
      super(service);
   }

   @Override
   public abstract CacheInformation.Cache getCache(String cacheName);

   protected abstract class Cache extends Infinispan52CacheInfo.Cache {
      public Cache(AdvancedCache cache) {
         super(cache);
      }

      @Override
      public int getEntryOverhead() {
         return 136;
      }
   }
}

package org.radargun.service;

import java.util.Map;

import org.infinispan.AdvancedCache;
import org.radargun.traits.CacheInformation;

/**
 * @author Radim Vansa &lt;rvansa@redhat.com&gt;
 */
public abstract class Infinispan52CacheInfo extends InfinispanCacheInfo {

   public Infinispan52CacheInfo(InfinispanEmbeddedService service) {
      super(service);
   }

   @Override
   public abstract CacheInformation.Cache getCache(String cacheName);

   protected abstract class Cache extends InfinispanCacheInfo.Cache {
      public Cache(AdvancedCache cache) {
         super(cache);
      }

      @Override
      public abstract Map<?, Long> getStructuredSize();

      @Override
      public int getEntryOverhead() {
         return 152;
      }

      @Override
      public long getTotalSize() {
         return Infinispan52CacheInfoTotalSize.getTotalSize(cache);
      }
   }
}

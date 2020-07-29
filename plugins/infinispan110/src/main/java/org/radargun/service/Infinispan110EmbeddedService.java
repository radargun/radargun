package org.radargun.service;

import org.infinispan.Cache;
import org.infinispan.configuration.global.GlobalConfiguration;
import org.radargun.Service;
import org.radargun.traits.ProvidesTrait;

@Service(doc = InfinispanEmbeddedService.SERVICE_DESCRIPTION)
public class Infinispan110EmbeddedService extends Infinispan101EmbeddedService {

   // it was being used to init EvenSpreadingConsistentHash but EvenSpreadingConsistentHash was removed
   protected void injectEvenConsistentHash(Cache<Object, Object> cache) {
   }

   @Override
   @ProvidesTrait
   public InfinispanCacheInfo createCacheInformation() {
      return new Infinispan110CacheInfo(this);
   }

   @Override
   @ProvidesTrait
   public InfinispanEmbeddedQueryable createQueryable() {
      return new Infinispan110EmbeddedQueryable(this);
   }

   @Override
   protected boolean isJmxEnabled() {
      GlobalConfiguration global = cacheManager.getCacheManagerConfiguration();
      boolean enabled = global.jmx().enabled();
      return enabled;
   }
}

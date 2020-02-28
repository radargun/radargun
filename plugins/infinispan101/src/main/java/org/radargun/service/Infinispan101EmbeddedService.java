package org.radargun.service;

import org.radargun.Service;
import org.radargun.traits.ProvidesTrait;

@Service(doc = InfinispanEmbeddedService.SERVICE_DESCRIPTION)
public class Infinispan101EmbeddedService extends Infinispan100EmbeddedService {

   @ProvidesTrait
   public EmbeddedConfigurationProvider createConfigurationProvider() {
      return new EmbeddedConfigurationProvider101(this);
   }

   @Override
   protected String getJmxDomain() {
      return JmxHelper101.getJmxDomain(cacheManager.getCacheManagerConfiguration());
   }

   @Override
   protected ConfigDumpHelper createConfigDumpHelper() {
      return new ConfigDumpHelper101();
   }
}

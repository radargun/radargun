package org.radargun.service;

import org.radargun.Service;
import org.radargun.traits.ProvidesTrait;

@Service(doc = InfinispanEmbeddedService.SERVICE_DESCRIPTION)
public class Infinispan110EmbeddedService extends Infinispan100EmbeddedService {

   @ProvidesTrait
   public EmbeddedConfigurationProvider createConfigurationProvider() {
      return new EmbeddedConfigurationProvider110(this);
   }

   @Override
   protected String getJmxDomain() {
      return JmxHelper110.getJmxDomain(cacheManager.getCacheManagerConfiguration());
   }

   @Override
   protected ConfigDumpHelper createConfigDumpHelper() {
      return new ConfigDumpHelper110();
   }
}

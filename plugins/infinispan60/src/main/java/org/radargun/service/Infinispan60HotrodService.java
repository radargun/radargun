package org.radargun.service;

import org.infinispan.client.hotrod.RemoteCacheManager;
import org.infinispan.client.hotrod.configuration.ConfigurationBuilder;
import org.radargun.Service;
import org.radargun.config.Init;

@Service(doc = Infinispan60HotrodService.SERVICE_DESCRIPTION)
public class Infinispan60HotrodService extends InfinispanHotrodService {
   @Init
   @Override
   public void init() {
      ConfigurationBuilder builder = new ConfigurationBuilder();
      if (servers != null) {
         builder.addServers(servers);
      }
      manager = new RemoteCacheManager(builder.build(), false);
   }
}

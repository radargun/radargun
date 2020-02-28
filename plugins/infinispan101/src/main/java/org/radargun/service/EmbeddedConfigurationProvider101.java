package org.radargun.service;

import org.infinispan.configuration.global.GlobalConfiguration;

public class EmbeddedConfigurationProvider101 extends EmbeddedConfigurationProvider60 {

   public EmbeddedConfigurationProvider101(Infinispan60EmbeddedService service) {
      super(service);
   }

   @Override
   protected String getJmxDomain(GlobalConfiguration global) {
      return JmxHelper101.getJmxDomain(global);
   }
}

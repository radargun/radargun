package org.radargun.service;

import org.infinispan.configuration.global.GlobalConfiguration;

public class EmbeddedConfigurationProvider110 extends EmbeddedConfigurationProvider60 {

   public EmbeddedConfigurationProvider110(Infinispan60EmbeddedService service) {
      super(service);
   }

   @Override
   protected String getJmxDomain(GlobalConfiguration global) {
      return JmxHelper110.getJmxDomain(global);
   }
}

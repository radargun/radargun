package org.radargun.service;

import org.infinispan.client.hotrod.configuration.ConfigurationBuilder;
import org.infinispan.client.hotrod.configuration.TransactionMode;
import org.radargun.Service;

@Service(doc = Infinispan60HotrodService.SERVICE_DESCRIPTION)
public class Infinispan110HotrodService extends Infinispan100HotrodService {

   /*
    * https://issues.redhat.com/browse/ISPN-12096
    * Fixed in ISPN12. Don't remove this implementation. Overwrite the behavior on Infinispan 12
    */
   @Override
   protected void afterConfigurationPropertiesLoad(ConfigurationBuilder builder) {
      if (transactionMode != null) {
         builder.transaction().transactionMode(TransactionMode.valueOf(transactionMode));
      }
   }
}

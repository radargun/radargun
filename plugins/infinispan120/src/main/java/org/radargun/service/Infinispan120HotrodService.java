package org.radargun.service;

import org.infinispan.client.hotrod.configuration.ConfigurationBuilder;
import org.infinispan.client.hotrod.configuration.TransactionMode;
import org.infinispan.client.hotrod.transaction.lookup.GenericTransactionManagerLookup;
import org.infinispan.client.hotrod.transaction.lookup.RemoteTransactionManagerLookup;
import org.radargun.Service;

@Service(doc = Infinispan60HotrodService.SERVICE_DESCRIPTION)
public class Infinispan120HotrodService extends Infinispan110HotrodService {

   protected void configureTransaction(ConfigurationBuilder config) {
      if (transactionManagerLookup != null) {
         if (this.cacheName == null || this.cacheName.trim().isEmpty()) {
            throw new IllegalArgumentException("A cache name is required to configure transaction");
         }
         if (GenericTransactionManagerLookup.class.getName().equals(transactionManagerLookup)) {
            config.remoteCache(this.cacheName).transactionManagerLookup(GenericTransactionManagerLookup.getInstance());
         } else if (RemoteTransactionManagerLookup.class.getName().equals(transactionManagerLookup)) {
            config.remoteCache(this.cacheName).transactionManagerLookup(RemoteTransactionManagerLookup.getInstance());
         } else {
            throw new IllegalArgumentException(String.format("Unknown transactionManagerLookup: %s", transactionManagerLookup));
         }
      }
      if (transactionMode != null) {
         if (this.cacheName == null || this.cacheName.trim().isEmpty()) {
            throw new IllegalArgumentException("A cache name is required to configure transaction");
         }
         config.remoteCache(this.cacheName).transactionMode(TransactionMode.valueOf(transactionMode));
      }
      config.maxRetries(maxRetries).socketTimeout(connectionTimeout).connectionTimeout(connectionTimeout);
   }
}

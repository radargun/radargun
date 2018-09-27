package org.radargun.service;

import org.infinispan.client.hotrod.RemoteCache;
import org.infinispan.client.hotrod.configuration.ConfigurationBuilder;
import org.infinispan.client.hotrod.configuration.TransactionMode;
import org.infinispan.commons.tx.lookup.TransactionManagerLookup;
import org.radargun.Service;
import org.radargun.config.Property;
import org.radargun.traits.ProvidesTrait;

/**
 * @author Diego Lovison &lt;dlovison@redhat.com&gt;
 */
@Service(doc = Infinispan60HotrodService.SERVICE_DESCRIPTION)
public class Infinispan93HotrodService extends Infinispan92HotrodService implements InfinispanTransactionalService {

   @Property(doc = "The TransactionManager interface defines the methods that allow an application server to manage transaction boundaries")
   protected String transactionManagerLookup;

   @Property(doc ="The TransactionMode tells how the RemoteCache is going to interact with the TransactionManager. Default NONE")
   protected String transactionMode = TransactionMode.NONE.name();

   @ProvidesTrait
   public InfinispanTransactional createTransactional() {
      return new Infinispan93Transactional(this);
   }

   protected ConfigurationBuilder getDefaultHotRodConfig() {
      ConfigurationBuilder config = super.getDefaultHotRodConfig();
      if (transactionManagerLookup != null) {
         try {
            config.transaction().transactionManagerLookup((TransactionManagerLookup) Class.forName(transactionManagerLookup).newInstance());
         } catch (ClassNotFoundException e) {
            throw new IllegalArgumentException("Cannot find " + transactionManagerLookup + " in the classpath", e);
         } catch (IllegalAccessException e) {
            throw new IllegalArgumentException("Cannot initialize " + transactionManagerLookup, e);
         } catch (InstantiationException e) {
            throw new IllegalArgumentException("Cannot initialize " + transactionManagerLookup, e);
         }
      }
      if (transactionMode != null) {
         config.transaction().transactionMode(TransactionMode.valueOf(transactionMode));
      }
      return config;
   }

   @Override
   public boolean isEnlistExtraXAResource() {
      // TODO see: https://github.com/radargun/radargun/issues/568
      return false;
   }

   @Override
   public boolean isCacheTransactional(String cacheName) {
      boolean isCacheTransactional = false;
      if (cacheName == null) {
         cacheName = this.cacheName;
      }
      if (managerNoReturn != null) {
         RemoteCache remoteCache;
         if (cacheName == null) {
            remoteCache = managerNoReturn.getCache();
         } else {
            remoteCache = managerNoReturn.getCache(cacheName);
         }
         isCacheTransactional = remoteCache.getTransactionManager() != null;
      }
      return isCacheTransactional;
   }

   @Override
   public boolean isBatching() {
      // Hot Rod does not support batch
      return false;
   }

   @Override
   public boolean isCacheBatching(String cacheName) {
      // Hot Rod does not support batch
      return false;
   }
}
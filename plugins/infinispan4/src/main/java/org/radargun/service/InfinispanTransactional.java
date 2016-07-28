package org.radargun.service;

import javax.transaction.TransactionManager;

import org.infinispan.AdvancedCache;
import org.radargun.logging.Log;
import org.radargun.logging.LogFactory;
import org.radargun.traits.Transactional;

public class InfinispanTransactional implements Transactional {
   protected static final Log log = LogFactory.getLog(InfinispanTransactional.class);
   protected static final boolean trace = log.isTraceEnabled();

   protected final InfinispanEmbeddedService service;
   protected final boolean enlistExtraXAResource;

   public InfinispanTransactional(InfinispanEmbeddedService service) {
      this.service = service;
      this.enlistExtraXAResource = service.enlistExtraXAResource;
   }

   @Override
   public Configuration getConfiguration(String cacheName) {
      return service.isCacheTransactional(service.getCache(cacheName)) ?
         Configuration.TRANSACTIONAL : Configuration.NON_TRANSACTIONAL;
   }

   @Override
   public Transaction getTransaction() {
      return new Tx();
   }

   protected AdvancedCache getAdvancedCache(Object resource) {
      if (resource == null) {
         return null;
      } else if (resource instanceof AdvancedCacheHolder) {
         return ((AdvancedCacheHolder) resource).getAdvancedCache();
      } else {
         throw new IllegalArgumentException(String.valueOf(resource));
      }
   }

   protected class Tx implements Transaction {
      protected TransactionManager tm;

      @Override
      public <T> T wrap(T resource) {
         if (resource == null) {
            return null;
         }
         TransactionManager tm = getAdvancedCache(resource).getTransactionManager();
         if (this.tm != null && this.tm != tm) {
            throw new IllegalArgumentException("Different transaction managers for single transaction!");
         }
         this.tm = tm;
         // we don't have to wrap anything for Infinispan
         return resource;
      }

      @Override
      public void begin() {
         try {
            tm.begin();
            javax.transaction.Transaction transaction = tm.getTransaction();
            if (trace) log.trace("Transaction begin " + transaction);
            if (enlistExtraXAResource) {
               transaction.enlistResource(new DummyXAResource());
            }
         } catch (Exception e) {
            throw new RuntimeException(e);
         }
      }

      @Override
      public void commit() {
         try {
            if (trace) log.trace("Transaction commit " + tm.getTransaction());
            tm.commit();
         } catch (Exception e) {
            throw new RuntimeException(e);
         }
      }

      @Override
      public void rollback() {
         try {
            if (trace) log.trace("Transaction rollback " + tm.getTransaction());
            tm.rollback();
         } catch (Exception e) {
            throw new RuntimeException(e);
         }
      }
   }
}

package org.radargun.service;

import javax.transaction.TransactionManager;

import org.radargun.logging.Log;
import org.radargun.logging.LogFactory;
import org.radargun.traits.Transactional;

/**
 * @author Diego Lovison &lt;dlovison@redhat.com&gt;
 */
public class Infinispan93HotRodTransactional implements Transactional {
   protected static final Log log = LogFactory.getLog(Infinispan93HotRodTransactional.class);
   protected static final boolean trace = log.isTraceEnabled();

   protected final Infinispan93HotrodService service;

   public Infinispan93HotRodTransactional(Infinispan93HotrodService service) {
      this.service = service;
   }

   @Override
   public Configuration getConfiguration(String cacheName) {
      return service.isCacheTransactional(cacheName) ?
         Configuration.TRANSACTIONAL : Configuration.NON_TRANSACTIONAL;
   }

   @Override
   public Transaction getTransaction() {
      return new Infinispan93HotRodTransactional.Tx();
   }

   protected <T> TransactionManager getTransactionManager(T resource) {
      return ((HotRodOperations.HotRodCache) resource).noReturn.getTransactionManager();
   }

   protected class Tx implements Transaction {
      protected TransactionManager tm;

      @Override
      public <T> T wrap(T resource) {
         if (resource == null) {
            return null;
         }
         TransactionManager tm = getTransactionManager(resource);
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

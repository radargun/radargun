package org.radargun.service;

import org.jboss.cache.transaction.DummyTransactionManager;
import org.radargun.traits.Transactional;

/**
 * @author Radim Vansa &lt;rvansa@redhat.com&gt;
 */
public class JBossCacheTransactional implements Transactional {
   @Override
   public Configuration getConfiguration(String resource) {
      return Configuration.TRANSACTIONS_ENABLED;
   }

   @Override
   public Transaction getTransaction() {
      return new Tx();
   }

   private class Tx implements Transaction {
      final DummyTransactionManager tm;

      private Tx() {
         this.tm = DummyTransactionManager.getInstance();
      }

      @Override
      public <T> T wrap(T resource) {
         return resource;
      }

      @Override
      public void begin() {
         try {
            tm.begin();
         } catch (Exception e) {
            throw new RuntimeException(e);
         }
      }

      @Override
      public void commit() {
         try {
            tm.commit();
         } catch (Exception e) {
            throw new RuntimeException(e);
         }
      }

      @Override
      public void rollback() {
         try {
            tm.rollback();
         } catch (Exception e) {
            throw new RuntimeException(e);
         }
      }
   }
}

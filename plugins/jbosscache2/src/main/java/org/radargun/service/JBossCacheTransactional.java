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
      private final DummyTransactionManager tm;
      private javax.transaction.Transaction transaction;

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
            transaction = tm.getTransaction();
         } catch (Exception e) {
            throw new RuntimeException(e);
         }
      }

      @Override
      public void commit() {
         try {
            transaction.commit();
         } catch (Exception e) {
            throw new RuntimeException(e);
         }
      }

      @Override
      public void rollback() {
         try {
            transaction.rollback();
         } catch (Exception e) {
            throw new RuntimeException(e);
         }
      }

      @Override
      public void suspend() {
         try {
            tm.suspend();
         } catch (Exception e) {
            throw new RuntimeException(e);
         }
      }

      @Override
      public void resume() {
         try {
            tm.resume(transaction);
         } catch (Exception e) {
            throw new RuntimeException(e);
         }
      }
   }
}

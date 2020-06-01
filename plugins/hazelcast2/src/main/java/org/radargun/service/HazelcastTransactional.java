package org.radargun.service;

import org.radargun.traits.Transactional;
import org.radargun.traits.Transactional.Configuration;

/**
 * Provides transactional operations for Hazelcast
 *
 * @author Radim Vansa &lt;rvansa@redhat.com&gt;
 */
public class HazelcastTransactional implements Transactional {
   protected final HazelcastService service;

   public HazelcastTransactional(HazelcastService service) {
      this.service = service;
   }

   @Override
   public Configuration getConfiguration(String cacheName) {
      if (service.useTransactions) {
         return Configuration.TRANSACTIONAL;
      } else {
         // Use transactions, if the stage requests it
         return Configuration.TRANSACTIONS_ENABLED;
      }
   }

   @Override
   public Transaction getTransaction() {
      return new Tx();
   }

   protected class Tx implements Transactional.Transaction {
      private final com.hazelcast.core.Transaction tx;

      public Tx() {
         this.tx = service.hazelcastInstance.getTransaction();
      }

      @Override
      public <T> T wrap(T resource) {
         return resource;
      }

      @Override
      public void begin() {
         tx.begin();
      }

      @Override
      public void commit() {
         tx.commit();
      }

      @Override
      public void rollback() {
         tx.rollback();
      }

      @Override
      public void suspend() {
         throw new UnsupportedOperationException();
      }

      @Override
      public void resume() {
         throw new UnsupportedOperationException();
      }
   }
}

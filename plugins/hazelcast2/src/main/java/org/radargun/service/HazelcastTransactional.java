package org.radargun.service;

import org.radargun.traits.Transactional;

/**
 * Provides transactional operations for Hazelcast
 */
public class HazelcastTransactional implements Transactional {
   protected final HazelcastService service;

   public HazelcastTransactional(HazelcastService service) {
      this.service = service;
   }

   @Override
   public Configuration getConfiguration(String cacheName) {
      return Configuration.TRANSACTIONS_ENABLED;
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
   }
}

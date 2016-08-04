package org.radargun.service;

import com.hazelcast.core.DistributedObject;
import com.hazelcast.core.HazelcastInstance;
import com.hazelcast.transaction.TransactionContext;
import org.radargun.logging.Log;
import org.radargun.logging.LogFactory;
import org.radargun.traits.Transactional;

/**
 * Provides transactional operations for Hazelcast
 *
 * @author Radim Vansa &lt;rvansa@redhat.com&gt;
 */
public class Hazelcast36Transactional implements Transactional {
   protected final Hazelcast36Service service;
   private static final Log log = LogFactory.getLog(Hazelcast36Transactional.class);
   private static final boolean trace = log.isTraceEnabled();

   public Hazelcast36Transactional(Hazelcast36Service service) {
      this.service = service;
   }

   @Override
   public Configuration getConfiguration(String cache) {
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

   private class Tx implements Transactional.Transaction {
      private TransactionContext transactionContext;
      private boolean started = false;

      public Tx() {
         transactionContext = ((HazelcastInstance) service.hazelcastInstance).newTransactionContext();
      }

      @Override
      public <T> T wrap(T resource) {
         if (resource == null) {
            return null;
         }
         if (!started) begin();
         if (resource instanceof DistributedObject) {
            return (T) transactionContext.getMap(((DistributedObject) resource).getName());
         } else if (resource instanceof Hazelcast36Operations.Cache) {
            String cacheName = ((Hazelcast36Operations.Cache) resource).map.getName();
            return (T) new Hazelcast36Operations.Cache(transactionContext.getMap(cacheName));
         } else if (resource instanceof HazelcastQuery.Context) {
            String cacheName = ((HazelcastQuery.Context) resource).map.getName();
            return (T) new HazelcastQuery.Context(transactionContext.getMap(cacheName));
         } else {
            throw new IllegalArgumentException(String.valueOf(resource));
         }
      }

      @Override
      public void begin() {
         if (trace) log.trace("Starting TX " + transactionContext.getTxnId());
         if (!started) {
            transactionContext.beginTransaction();
            started = true;
         }
      }

      @Override
      public void commit() {
         if (trace) log.trace("Committing TX " + transactionContext.getTxnId());
         transactionContext.commitTransaction();
      }

      @Override
      public void rollback() {
         if (trace) log.trace("Rolling back TX " + transactionContext.getTxnId());
         transactionContext.rollbackTransaction();
      }
   }
}

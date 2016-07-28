package org.radargun.stages.test;

import org.radargun.traits.Transactional;

/**
 * Defines whether transactions should be used when working with this service.
 */
public enum TransactionMode {
   NEVER,
   IF_TRANSACTIONAL,
   ALWAYS;

   public boolean use(Transactional transactional, String cacheName, int transactionSize) {
      switch (this) {
         case NEVER:
            return false;
         case ALWAYS:
            return true;
         case IF_TRANSACTIONAL:
            if (transactional == null) return false;
            Transactional.Configuration configuration = transactional.getConfiguration(cacheName);
            // default for Transactional.Configuration.TRANSACTIONS_ENABLED is without transactions
            return configuration != null && configuration == Transactional.Configuration.TRANSACTIONAL && transactionSize > 0;
         default:
            throw new IllegalStateException("Unknown state: " + this);
      }
   }
}

package org.radargun.stages.cache.background;

import java.util.concurrent.atomic.AtomicLong;

/**
 * Holder class for failures occurring during test run.
 *
 * @author Matej Cimbora
 * @author Radim Vansa
 */
public class FailureHolder {

   public FailureHolder() {
   }

   private AtomicLong missingOperations = new AtomicLong();
   private AtomicLong missingNotifications = new AtomicLong();
   private AtomicLong staleReads = new AtomicLong();
   private AtomicLong failedTransactionAttempts = new AtomicLong();
   private AtomicLong delayedRemovesErrors = new AtomicLong();

   public long getMissingOperations() {
      return missingOperations.get();
   }

   public long getMissingNotifications() {
      return missingNotifications.get();
   }

   public long getStaleReads() {
      return staleReads.get();
   }

   public long getFailedTransactionAttempts() {
      return failedTransactionAttempts.get();
   }

   public long getDelayedRemovesErrors() {
      return delayedRemovesErrors.get();
   }

   public void reportMissingOperation() {
      missingOperations.incrementAndGet();
   }

   public void reportMissingNotification() {
      missingNotifications.incrementAndGet();
   }

   public void reportStaleRead() {
      staleReads.incrementAndGet();
   }
   public void reportFailedTransactionAttempt() {
      failedTransactionAttempts.incrementAndGet();
   }

   public void reportDelayedRemoveError() {
      delayedRemovesErrors.incrementAndGet();
   }
}

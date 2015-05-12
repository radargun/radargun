package org.radargun.stages.cache.background;

import java.util.HashMap;
import java.util.Map;
import java.util.Random;

import org.radargun.stages.helpers.Range;
import org.radargun.traits.BasicOperations;
import org.radargun.utils.Utils;

/**
 * Logic based on log values. The general idea is that each operation on an entry
 * should be persisted in the value by appending the operation id to the value.
 * Therefore, the value works as a log. Old operations (confirmed on all nodes
 * are eventually unwound from the value).
 *
 * The key used for each operation in given stressor is deterministic. Instance
 * of Random with known seed is used on each node.
 *
 * The stressors (executing this logic) and {@link LogChecker checkers}
 * are synchronized by writing into special values. Once in a while, each stressor
 * confirms that it has written all operations with lower id by updating
 * the stressor_* entry. Also, it checks all checker_* keys to see that it can
 * unwind old records.
 *
 * When either stressor or checker is restarted, it should continue with the sequence
 * from the last confirmed point. Therefore, the stressor_* entry contains the current
 * seed of the Random and the stressor can load it.
 * Similar situation happens when the transaction is rolled back. We have to remember
 * the seeds before the transaction in order to be able to repeat it.
 *
 * @author Radim Vansa &lt;rvansa@redhat.com&gt;
 */
abstract class AbstractLogLogic<ValueType> extends AbstractLogic {

   protected final BasicOperations.Cache nonTxBasicCache;
   protected BasicOperations.Cache basicCache;

   protected final Random operationTypeRandom = new Random();

   protected Range keyRange;
   protected Random keySelectorRandom;
   protected volatile long operationId = 0;
   protected volatile long keyId;
   protected Map<Long, DelayedRemove> delayedRemoves = new HashMap<Long, DelayedRemove>();
   private volatile long txStartOperationId;
   private volatile long txStartKeyId = -1;
   private long txStartRandSeed;
   private boolean txRolledBack = false;
   private volatile long lastSuccessfulOpTimestamp;
   private volatile long lastSuccessfulTxTimestamp;
   private int remainingTxOps;
   private volatile long lastConfirmedOperation = -1;

   public AbstractLogLogic(BackgroundOpsManager manager, Range keyRange) {
      super(manager);
      this.keyRange = keyRange;
      this.nonTxBasicCache = manager.getBasicCache();
      if (transactionSize <= 0) {
         basicCache = nonTxBasicCache;
      }
      remainingTxOps = transactionSize;
   }

   /**
    * This operation has to be called within the stressor thread and should be sensitive to interruptions
    */
   public void init() {
      Random random = null;
      while (random == null && !Thread.currentThread().isInterrupted()) {
         try {
            Object last = nonTxBasicCache.get(LogChecker.lastOperationKey(stressor.id));
            if (last != null) {
               operationId = ((LogChecker.LastOperation) last).getOperationId() + 1;
               random = Utils.setRandomSeed(new Random(0), ((LogChecker.LastOperation) last).getSeed());
               log.debug("Restarting operations from operation " + operationId);
            } else {
               log.trace("Initializing stressor random with " + stressor.id);
               random = new Random(stressor.id);
            }
         } catch (Exception e) {
            // exception cannot be understood as 0 because of AvailabilityExceptions
            log.error("Failure getting last operation", e);
         }
      }
      keySelectorRandom = random;
   }

   @Override
   public void invoke() throws InterruptedException {
      keyId = (keySelectorRandom.nextLong() & Long.MAX_VALUE) % keyRange.getSize() + keyRange.getStart();
      do {
         if (txRolledBack) {
            keyId = txStartKeyId;
            operationId = txStartOperationId;
            Utils.setRandomSeed(keySelectorRandom, txStartRandSeed);
            txRolledBack = false;
         }
         if (trace) {
            log.trace("Operation " + operationId + " on key " + keyGenerator.generateKey(keyId));
         }
      } while (!invokeOn(keyId) && !stressor.isInterrupted() && !stressor.isTerminated());
      operationId++;
   }

   @Override
   public String getStatus() {
      long currentTime = System.currentTimeMillis();
      return String.format("current[id=%d, key=%s], lastSuccessfulOpTime=%d",
            operationId, keyGenerator.generateKey(keyId), lastSuccessfulOpTimestamp - currentTime)
            + (transactionSize > 0 ?
               String.format(", txStart[id=%d, key=%s], remainingTxOps=%d, lastSuccessfulTxTime=%d",
                     txStartOperationId, keyGenerator.generateKey(txStartKeyId), remainingTxOps,
                     lastSuccessfulTxTimestamp - currentTime) : "");
   }

   /* Return value = true: follow with next operation,
                    false: txRolledBack ? restart from txStartOperationId : retry operationId */
   protected boolean invokeOn(long keyId) throws InterruptedException {
      try {
         if (transactionSize > 0 && remainingTxOps == transactionSize) {
            txStartOperationId = operationId;
            txStartKeyId = keyId;
            // we could serialize & deserialize instead, but that's not much better
            txStartRandSeed = Utils.getRandomSeed(keySelectorRandom);
            startTransaction();
         }

         boolean txBreakRequest = false;
         try {
            if (!invokeLogic(keyId)) return false;
         } catch (BreakTxRequest request) {
            txBreakRequest = true;
         }
         lastSuccessfulOpTimestamp = System.currentTimeMillis();

         // for non-transactional caches write the stressor last operation anytime (once in a while)
         if (transactionSize <= 0 && operationId % manager.getLogLogicConfiguration().getCounterUpdatePeriod() == 0) {
            writeStressorLastOperation();
            lastConfirmedOperation = operationId;
         }

         if (transactionSize > 0) {
            remainingTxOps--;
            if (remainingTxOps <= 0 || txBreakRequest) {
               try {
                  ongoingTx.commit();
                  lastSuccessfulTxTimestamp = System.currentTimeMillis();
               } catch (Exception e) {
                  log.trace("Transaction was rolled back, restarting from operation " + txStartOperationId);
                  txRolledBack = true;
                  afterRollback();
                  return false;
               } finally {
                  remainingTxOps = transactionSize;
                  clearTransaction();
               }
               if (stressor.isTerminated()) {
                  // If the thread was interrupted and cache is registered as Synchronization (not XAResource)
                  // commit phase may fail but no exception is thrown. Therefore, we should terminate immediatelly
                  // as we don't want to remove entries while the modifications have not been written.
                  log.info("Thread is about to terminate, not executing delayed removes");
                  return false;
               }
               afterCommit();
               if (stressor.isTerminated()) {
                  // the removes may have failed and we have not repeated them due to termination
                  log.info("Thread is about to terminate, not writing the last operation");
                  return false;
               }
               if (txBreakRequest) {
                  log.trace("Transaction was committed sooner, retrying operation " + operationId);
                  return false;
               }

               // for non-transactional caches write the stressor last operation only after the transaction
               // has finished
               try {
                  startTransaction();
                  writeStressorLastOperation();
                  ongoingTx.commit();
                  lastConfirmedOperation = operationId;
               } catch (Exception e) {
                  log.error("Cannot write stressor last operation", e);
               } finally {
                  clearTransaction();
               }
            }
         }
         return true;
      } catch (Exception e) {
         InterruptedException ie = Utils.findThrowableCauseByClass(e, InterruptedException.class);
         if (ie != null) {
            throw ie;
         } else if (e.getClass().getName().contains("SuspectException")) {
            log.error("Request failed due to SuspectException: " + e.getMessage());
         } else {
            log.error("Cache operation error", e);
         }
         if (transactionSize > 0 && ongoingTx != null) {
            try {
               ongoingTx.rollback();
               log.info("Transaction rolled back");
            } catch (Exception e1) {
               log.error("Error while rolling back transaction", e1);
            } finally {
               log.info("Restarting from operation " + txStartOperationId);
               clearTransaction();
               remainingTxOps = transactionSize;
               txRolledBack = true;
               afterRollback();
            }
         }
         return false; // on the same key
      }
   }

   protected void afterRollback() {
      delayedRemoves.clear();
   }

   protected void afterCommit() {
      boolean inTransaction = false;
      try {
         while (!stressor.isTerminated()) {
            try {
               if (inTransaction) {
                  try {
                     ongoingTx.rollback();
                  } catch (Exception e) {
                     log.error("Failed to rollback ongoing transaction", e);
                  }
               }
               startTransaction();
               inTransaction = true;
               for (DelayedRemove delayedRemove : delayedRemoves.values()) {
                  checkedRemoveValue(delayedRemove.keyId, delayedRemove.oldValue);
               }
               ongoingTx.commit();
               lastSuccessfulTxTimestamp = System.currentTimeMillis();
               inTransaction = false;
               delayedRemoves.clear();
               return;
            } catch (Exception e) {
               log.error("Error while executing delayed removes.", e);
            }
         }
      } finally {
         clearTransaction();
      }
   }

   protected void startTransaction() {
      ongoingTx = manager.newTransaction();
      basicCache = ongoingTx.wrap(nonTxBasicCache);
      ongoingTx.begin();
   }

   protected void clearTransaction() {
      ongoingTx = null;
      basicCache = null;
   }

   protected void delayedRemoveValue(long keyId, ValueType prevValue) throws Exception {
      if (transactionSize <= 0) {
         checkedRemoveValue(keyId, prevValue);
      } else {
         // if we moved around the key within one transaction multiple times we don't want to delete the complement
         delayedRemoves.remove(~keyId);
         delayedRemoves.put(keyId, new DelayedRemove(keyId, prevValue));
      }
   }

   protected abstract boolean checkedRemoveValue(long keyId, ValueType oldValue) throws Exception;

   private void writeStressorLastOperation() {
      try {
         // we have to write down the keySelectorRandom as well in order to be able to continue work if this slave
         // is restarted
         basicCache.put(LogChecker.lastOperationKey(stressor.id),
               new LogChecker.LastOperation(operationId, Utils.getRandomSeed(keySelectorRandom)));
      } catch (Exception e) {
         log.error("Error writing stressor last operation", e);
      }
   }

   protected abstract boolean invokeLogic(long keyId) throws Exception;

   /**
    * Returns minimum of checked (confirmed) operations for given stressor thread across all nodes.
    */
   protected long getCheckedOperation(int thread, long minOperationId) throws StressorException, BreakTxRequest {
      long minimumOperationId = Long.MAX_VALUE;
      for (int i = 0; i < manager.getSlaveState().getGroupSize(); ++i) {
         Object lastCheckedOperationId;
         try {
            lastCheckedOperationId = basicCache.get(LogChecker.checkerKey(i, thread));
         } catch (Exception e) {
            log.error("Cannot read last checked operation id for slave " + i + " and thread " + thread, e);
            throw new StressorException(e);
         }
         long readOperationId = lastCheckedOperationId == null ? Long.MIN_VALUE : ((LogChecker.LastOperation) lastCheckedOperationId).getOperationId();
         if (readOperationId < minOperationId && manager.getLogLogicConfiguration().isIgnoreDeadCheckers() && !manager.isSlaveAlive(i)) {
            try {
               Object ignored = basicCache.get(LogChecker.ignoredKey(i, thread));
               if (ignored == null || (Long) ignored < minOperationId) {
                  log.debugf("Setting ignore operation for checker slave %d and stressor %d: %s -> %d (last check %s)",
                             i, thread, ignored, minOperationId, lastCheckedOperationId);
                  basicCache.put(LogChecker.ignoredKey(i, thread), minOperationId);
                  if (transactionSize > 0) {
                     throw new BreakTxRequest();
                  }
               }
               minimumOperationId = Math.min(minimumOperationId, minOperationId);
            } catch (BreakTxRequest request) {
               throw request;
            } catch (Exception e) {
               log.error("Cannot overwrite last checked operation id for slave " + i + " and thread " + thread, e);
               throw new StressorException(e);
            }
         } else {
            minimumOperationId = Math.min(minimumOperationId, readOperationId);
         }
      }
      return minimumOperationId;
   }

   public long getLastConfirmedOperation() {
      return lastConfirmedOperation;
   }

   protected class DelayedRemove {
      public final long keyId;
      public final ValueType oldValue;

      protected DelayedRemove(long keyId, ValueType oldValue) {
         this.keyId = keyId;
         this.oldValue = oldValue;
      }
   }
}

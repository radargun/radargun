package org.radargun.stages.cache.background;

import java.util.HashMap;
import java.util.Map;
import java.util.Random;

import org.radargun.stages.helpers.Range;
import org.radargun.traits.BasicOperations;
import org.radargun.utils.TimeService;
import org.radargun.utils.Utils;

import static org.radargun.stages.cache.background.LogChecker.LastOperation;

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
   protected Map<Long, DelayedRemove> delayedRemoves = new HashMap<>();
   private volatile long txStartOperationId;
   private volatile long txStartKeyId = -1;
   private long txStartRandSeed;
   private boolean txRolledBack = false;
   private volatile long lastSuccessfulOpTimestamp;
   private volatile long lastSuccessfulTxTimestamp;
   private int remainingTxOps;
   private volatile long lastConfirmedOperation = -1;
   private volatile int txFailedAttempts;

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
            LastOperation lastOperation = (LastOperation) nonTxBasicCache.get(LogChecker.lastOperationKey(stressor.id));
            if (lastOperation != null) {
               operationId = lastOperation.getOperationId() + 1;
               random = Utils.setRandomSeed(new Random(0), lastOperation.getSeed());
               log.debugf("Restarting operations from operation %d", operationId);
            } else {
               log.tracef("Initializing stressor random with %d", stressor.id);
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
            txFailedAttempts++;
            log.tracef("Transaction rollbacked, number of attempts so far=%d", txFailedAttempts);
            if (manager.getLogLogicConfiguration().getMaxTransactionAttempts() >= 0
               && txFailedAttempts > manager.getLogLogicConfiguration().getMaxTransactionAttempts()) {
               log.error("Maximum number of transaction attempts attained, reporting.");
               manager.getFailureManager().reportFailedTransactionAttempt();
            }
         }
         if (trace) {
            log.tracef("Operation %d on key %s", operationId, keyGenerator.generateKey(keyId));
         }
      } while (!invokeOn(keyId) && !stressor.isInterrupted() && !stressor.isTerminated());
      operationId++;
   }

   @Override
   public String getStatus() {
      long currentTime = TimeService.currentTimeMillis();
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
         lastSuccessfulOpTimestamp = TimeService.currentTimeMillis();

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
                  lastSuccessfulTxTimestamp = TimeService.currentTimeMillis();
                  txFailedAttempts = 0;
               } catch (Exception e) {
                  log.debugf("Transaction %s was rolled back, restarting from operation %d", ongoingTx, txStartOperationId);
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
                  log.debugf("Stressor %s is about to terminate, not executing delayed removes", stressor.getStatus());
                  return false;
               }
               afterCommit();
               if (stressor.isTerminated()) {
                  // the removes may have failed and we have not repeated them due to termination
                  log.debugf("Stressor %s is about to terminate, not writing the last operation %d", stressor.getStatus(), operationId);
                  return false;
               }
               if (txBreakRequest) {
                  log.debugf("Transaction was committed sooner, retrying operation %d", operationId);
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
               log.errorf("Transaction %s rolled back", ongoingTx);
            } catch (Exception e1) {
               log.errorf(e1, "Error while rolling back transaction %s", ongoingTx);
            } finally {
               log.debugf("Restarting from operation %d, current operation %d", txStartOperationId, operationId);
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

   protected boolean afterCommit() {
      try {
         int delayedRemoveAttempts = 0;
         while (!stressor.isTerminated()) {
            boolean delayedRemoveError = false;
            try {
               if (ongoingTx != null) {
                  try {
                     ongoingTx.rollback();
                  } catch (Exception e) {
                     log.error("Failed to rollback ongoing transaction", e);
                  }
               }
               startTransaction();
               for (DelayedRemove delayedRemove : delayedRemoves.values()) {
                  try {
                     // avoid infinite loops -> try 'maxDelayedRemoveAttempts' times
                     if (manager.getLogLogicConfiguration().getMaxDelayedRemoveAttempts() >= 0 && delayedRemoveAttempts > manager.getLogLogicConfiguration().getMaxDelayedRemoveAttempts()) {
                        log.errorf("Maximum number of delayed remove attempts on key %s attained, reporting.", keyGenerator.generateKey(delayedRemove.keyId));
                        manager.getFailureManager().reportDelayedRemoveError();
                        stressor.requestTerminate();
                        return false;
                     }
                     checkedRemoveValue(delayedRemove.keyId, delayedRemove.oldValue);
                  } catch (Exception e) {
                     if (manager.getLogLogicConfiguration().getMaxDelayedRemoveAttempts() >= 0) {
                        delayedRemoveAttempts++;
                     }
                     delayedRemoveError = true;
                     throw e;
                  }
               }
               ongoingTx.commit();
               lastSuccessfulTxTimestamp = TimeService.currentTimeMillis();
               delayedRemoves.clear();
               return true;
            } catch (Exception e) {
               // Record exceptions not originating from checkedRemoveValue (e.g. tx.commit)
               if (!delayedRemoveError && manager.getLogLogicConfiguration().getMaxDelayedRemoveAttempts() >= 0) {
                  delayedRemoveAttempts++;
               }
               log.error("Error while executing delayed removes.", e);
            } finally {
               delayedRemoveError = false;
            }
         }
      } finally {
         clearTransaction();
      }
      return true;
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

   protected void writeStressorLastOperation() {
      try {
         // we have to write down the keySelectorRandom as well in order to be able to continue work if this worker
         // is restarted
         basicCache.put(LogChecker.lastOperationKey(stressor.id),
            new LastOperation(operationId, Utils.getRandomSeed(keySelectorRandom)));
      } catch (Exception e) {
         log.errorf(e, "Error while writing last operation %d for stressor %s", operationId, stressor.getStatus());
      }
   }

   protected abstract boolean invokeLogic(long keyId) throws Exception;

   /**
    * Returns minimum of checked (confirmed) operations for given stressor thread across all nodes.
    */
   protected long getCheckedOperation(int stressorId, long operationId) throws StressorException, BreakTxRequest {
      long minCheckedOperation = Long.MAX_VALUE;
      for (int i = 0; i < manager.getWorkerState().getGroupSize(); ++i) {
         long lastCheckedOperationId = Long.MIN_VALUE;
         try {
            LastOperation lastOperation = (LastOperation) basicCache.get(LogChecker.checkerKey(i, stressorId));
            if (lastOperation != null) {
               lastCheckedOperationId = lastOperation.getOperationId();
            }
         } catch (Exception e) {
            log.errorf(e, "Cannot read last checked operation id for worker %d, stressor %d", i, stressorId);
            throw new StressorException(e);
         }
         if (lastCheckedOperationId < operationId && manager.getLogLogicConfiguration().isIgnoreDeadCheckers() && !manager.isWorkerAlive(i)) {
            try {
               Long ignoredOperationId = (Long) basicCache.get(LogChecker.ignoredKey(i, stressorId));
               if (ignoredOperationId == null || ignoredOperationId < operationId) {
                  log.tracef("Setting ignore operation for checker worker %d and stressor %d: %d -> %d (last checked operation %d)",
                     i, stressorId, ignoredOperationId, operationId, lastCheckedOperationId);
                  basicCache.put(LogChecker.ignoredKey(i, stressorId), operationId);
                  if (transactionSize > 0) {
                     throw new BreakTxRequest();
                  }
               }
               minCheckedOperation = Math.min(minCheckedOperation, operationId);
            } catch (BreakTxRequest request) {
               throw request;
            } catch (Exception e) {
               log.errorf(e, "Cannot overwrite ignored operation id for worker %d", "stressor %d", i, stressorId);
               throw new StressorException(e);
            }
         } else {
            minCheckedOperation = Math.min(minCheckedOperation, lastCheckedOperationId);
         }
      }
      return minCheckedOperation;
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

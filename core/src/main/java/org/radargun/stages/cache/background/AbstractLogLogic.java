package org.radargun.stages.cache.background;

import org.radargun.traits.BasicOperations;
import org.radargun.utils.Utils;

import java.util.HashMap;
import java.util.Map;
import java.util.Random;

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

   protected BasicOperations.Cache basicCache;

   protected final Random keySelectorRandom;
   protected final Random operationTypeRandom = new Random();

   protected volatile long operationId = 0;
   protected volatile long keyId;
   protected Map<Long, DelayedRemove> delayedRemoves = new HashMap<Long, DelayedRemove>();
   private volatile long txStartOperationId;
   private volatile long txStartKeyId = -1;
   private long txStartRandSeed;
   private boolean txRolledBack = false;
   private volatile long lastSuccessfulOpTimestamp;
   private volatile long lastSuccessfulTxTimestamp;
   protected int remainingTxOps;

   public AbstractLogLogic(BackgroundOpsManager manager, long stressorId) {
      super(manager);
      this.basicCache = manager.getBasicCache();

      Random rand = null;
      try {
         Object last = basicCache.get(LogChecker.lastOperationKey((int) stressorId));
         if (last != null) {
            operationId = ((LogChecker.LastOperation) last).getOperationId() + 1;
            rand = Utils.setRandomSeed(new Random(0), ((LogChecker.LastOperation) last).getSeed());
            log.debug("Restarting operations from operation " + operationId);
         }
      } catch (Exception e) {
         log.error("Failure getting last operation", e);
      }
      if (rand == null) {
         log.trace("Initializing stressor random with " + stressorId);
         this.keySelectorRandom = new Random(stressorId);
      } else {
         this.keySelectorRandom = rand;
      }
      remainingTxOps = transactionSize;
   }

   @Override
   public void invoke() throws InterruptedException {
      keyId = nextKeyId();
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

   protected abstract long nextKeyId();

   /* Return value = true: follow with next operation,
                    false: txRolledBack ? restart from txStartOperationId : retry operationId */
   protected boolean invokeOn(long keyId) throws InterruptedException {
      try {
         if (transactionSize > 0 && remainingTxOps == transactionSize) {
            txStartOperationId = operationId;
            txStartKeyId = keyId;
            // we could serialize & deserialize instead, but that's not much better
            txStartRandSeed = Utils.getRandomSeed(keySelectorRandom);
            txCache.startTransaction();
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
         }

         if (transactionSize > 0) {
            remainingTxOps--;
            if (remainingTxOps <= 0 || txBreakRequest) {
               try {
                  txCache.endTransaction(true);
                  lastSuccessfulTxTimestamp = System.currentTimeMillis();
               } catch (Exception e) {
                  log.trace("Transaction was rolled back, restarting from operation " + txStartOperationId);
                  txRolledBack = true;
                  afterRollback();
                  return false;
               } finally {
                  remainingTxOps = transactionSize;
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
                  txCache.startTransaction();
                  writeStressorLastOperation();
                  txCache.endTransaction(true);
               } catch (Exception e) {
                  log.error("Cannot write stressor last operation", e);
               }
            }
         }
         return true;
      } catch (Exception e) {
         InterruptedException ie = findInterruptionCause(null, e);
         if (ie != null) {
            throw ie;
         } else if (e.getClass().getName().contains("SuspectException")) {
            log.error("Request failed due to SuspectException: " + e.getMessage());
         } else {
            log.error("Cache operation error", e);
         }
         if (transactionSize > 0) {
            try {
               txCache.endTransaction(false);
               log.info("Transaction rolled back");
            } catch (Exception e1) {
               log.error("Error while rolling back transaction", e1);
            } finally {
               log.info("Restarting from operation " + txStartOperationId);
               remainingTxOps = transactionSize;
               txRolledBack = true;
               afterRollback();
            }
         }
         return false; // on the same key
      }
   }

   private void afterRollback() {
      delayedRemoves.clear();
   }

   private void afterCommit() {
      boolean inTransaction = false;
      while (!stressor.isTerminated()) {
         try {
            if (inTransaction) {
               try {
                  txCache.endTransaction(false);
               } catch (Exception e) {
               }
            }
            txCache.startTransaction();
            inTransaction = true;
            for (DelayedRemove delayedRemove : delayedRemoves.values()) {
               checkedRemoveValue(delayedRemove.keyId, delayedRemove.oldValue);
            }
            txCache.endTransaction(true);
            lastSuccessfulTxTimestamp = System.currentTimeMillis();
            inTransaction = false;
            delayedRemoves.clear();
            return;
         } catch (Exception e) {
            log.error("Error while executing delayed removes.", e);
         }
      }
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

   protected Map<Integer, Long> getCheckedOperations(long minOperationId) throws StressorException, BreakTxRequest {
      Map<Integer, Long> minIds = new HashMap<Integer, Long>();
      for (int thread = 0; thread < manager.getGeneralConfiguration().getNumThreads() * manager.getClusterSize(); ++thread) {
         minIds.put(thread, getCheckedOperation(thread, minOperationId));
      }
      return minIds;
   }

   protected long getCheckedOperation(int thread, long minOperationId) throws StressorException, BreakTxRequest {
      long minReadOperationId = Long.MAX_VALUE;
      for (int i = 0; i < manager.getClusterSize(); ++i) {
         Object lastCheck;
         try {
            lastCheck = basicCache.get(LogChecker.checkerKey(i, thread));
         } catch (Exception e) {
            log.error("Cannot read last checked operation id for slave " + i + " and thread " + thread, e);
            throw new StressorException(e);
         }
         long readOperationId = lastCheck == null ? Long.MIN_VALUE : ((LogChecker.LastOperation) lastCheck).getOperationId();
         if (readOperationId < minOperationId && manager.getLogLogicConfiguration().isIgnoreDeadCheckers() && !manager.isSlaveAlive(i)) {
            try {
               Object ignored = basicCache.get(LogChecker.ignoredKey(i, thread));
               if (ignored == null || (Long) ignored < minOperationId) {
                  log.debug(String.format("Setting ignore operation for checker slave %d and stressor %d: %s -> %d (last check %s)",
                        i, thread, ignored, minOperationId, lastCheck));
                  basicCache.put(LogChecker.ignoredKey(i, thread), minOperationId);
                  if (transactionSize > 0) {
                     throw new BreakTxRequest();
                  }
               }
               minReadOperationId = Math.min(minReadOperationId, minOperationId);
            } catch (BreakTxRequest request) {
               throw request;
            } catch (Exception e) {
               log.error("Cannot overwrite last checked operation id for slave " + i + " and thread " + thread, e);
               throw new StressorException(e);
            }
         } else {
            minReadOperationId = Math.min(minReadOperationId, readOperationId);
         }
      }
      return minReadOperationId;
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

package org.radargun.stages.cache.background;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

import org.radargun.Operation;
import org.radargun.stages.helpers.Range;
import org.radargun.stats.Request;
import org.radargun.traits.BasicOperations;
import org.radargun.utils.TimeService;

/**
 * This logic operates on {@link PrivateLogValue private log values} using only {@link BasicOperations},
 * specifically put, get and remove operations. Private log values are written to by single thread.
 *
 * @author Radim Vansa &lt;rvansa@redhat.com&gt;
 */
class PrivateLogLogic extends AbstractLogLogic<PrivateLogValue> {

   // Timestamps of the last writes into given values. As we can get stale read for some period,
   // we cannot overwrite the value again until we can be sure that we can safely read current
   // value of that entry. We have to keep the timestamps here, as we cannot reliably determine
   // the last write timestamp from cache (because any value read could be stale).
   // However, we still can't wait the timeout when we decide to overwrite an entry -
   // due to the birthday paradox, this happens very often. We have to find out ourselves whether
   // the read was stale or not.
   // Note that this does not cause any problems with SharedLogLogic since all updates there are
   // conditional; if stale value is read, the conditional operation will fail (stale read must
   // not happen during the condition verification).
   private final Map<Long, OperationTimestampPair> timestamps = new HashMap<>();
   // Keys modified during current transaction, should be recorded to timestamps when
   // the transaction is committed
   private final Collection<KeyOperationPair> txModifications = new ArrayList<>(Math.max(0, transactionSize));
   private final Map<Long, Long> maxPrunedOperationIds = new HashMap<>(2);

   PrivateLogLogic(BackgroundOpsManager manager, Range range) {
      super(manager, range);
   }

   @Override
   protected boolean invokeLogic(long keyId) throws Exception {
      Operation operation = getOperation(operationTypeRandom);

      OperationTimestampPair prevOperation = timestamps.get(keyId);
      // first we have to get the value
      PrivateLogValue prevValue = checkedGetValue(keyId);
      PrivateLogValue backupValue = null;
      if (prevOperation != null) {
         if (prevValue == null || !prevValue.contains(prevOperation.operationId)) {
            // non-cleaned old value or stale read, try backup
            backupValue = checkedGetValue(~keyId);
            boolean txEnabled = manager.getGeneralConfiguration().getTransactionSize() > 0;
            // Modifying the same key within a single transaction may cause false stale reads, avoid it by checking maxPrunedOperationIds
            boolean valuePruned = maxPrunedOperationIds.get(keyId) != null && maxPrunedOperationIds.get(keyId) >= prevOperation.operationId;
            if ((backupValue == null || !backupValue.contains(prevOperation.operationId)) && (!txEnabled || !valuePruned)) {
               // definitely stale read
               log.debugf("Detected stale read, keyId=%s, previousValue=%s, complementValue=%s", keyId, prevValue, backupValue);
               waitForStaleRead(prevOperation.timestamp);
               return false;
            } else {
               if (!txEnabled || !valuePruned) {
                  // pretend that we haven't read it at all
                  prevValue = null;
               }
            }
         }
      }
      if (operation == BasicOperations.GET) {
         // especially GETs are not allowed here, because these would break the deterministic order
         // - each operationId must be written somewhere
         throw new UnsupportedOperationException("Only PUT and REMOVE operations are allowed for this logic");
      } else if (prevValue == null || operation == BasicOperations.PUT) {
         PrivateLogValue nextValue;
         if (prevValue != null) {
            nextValue = getNextValue(keyId, prevValue);
         } else {
            // the value may have been removed, look for backup
            if (backupValue == null) {
               backupValue = checkedGetValue(~keyId);
            }
            if (backupValue == null) {
               nextValue = new PrivateLogValue(stressor.id, operationId);
            } else {
               nextValue = getNextValue(keyId, backupValue);
            }
         }
         if (nextValue == null) {
            return false;
         }
         checkedPutValue(keyId, nextValue);
         if (backupValue != null) {
            delayedRemoveValue(~keyId, backupValue);
         }
      } else if (operation == BasicOperations.REMOVE) {
         PrivateLogValue nextValue = getNextValue(keyId, prevValue);
         if (nextValue == null) {
            return false;
         }
         checkedPutValue(~keyId, nextValue);
         delayedRemoveValue(keyId, prevValue);
      }

      if (transactionSize > 0) {
         txModifications.add(new KeyOperationPair(keyId, operationId));
      } else {
         long now = TimeService.currentTimeMillis();
         timestamps.put(keyId, new OperationTimestampPair(operationId, now));
         log.tracef("Operation %d on %08X finished at %d", operationId, keyId, now);
      }
      return true;
   }

   private void waitForStaleRead(long lastWriteTimestamp) throws InterruptedException {
      long writeApplyMaxDelay = manager.getLogLogicConfiguration().writeApplyMaxDelay;
      if (writeApplyMaxDelay > 0) {
         long now = TimeService.currentTimeMillis();
         if (lastWriteTimestamp > now - writeApplyMaxDelay) {
            log.debugf("Last write of %08X was at %d, waiting 5 seconds to evade stale reads", keyId);
            Thread.sleep(5000);
         }
      } else {
         manager.getFailureManager().reportStaleRead();
         stressor.requestTerminate();
      }
   }

   @Override
   protected void afterRollback() {
      super.afterRollback();
      txModifications.clear();
      maxPrunedOperationIds.clear();
   }

   @Override
   protected boolean afterCommit() {
      boolean result = super.afterCommit();
      if (result) {
         long now = TimeService.currentTimeMillis();
         for (KeyOperationPair pair : txModifications) {
            timestamps.put(pair.keyId, new OperationTimestampPair(pair.operationId, now));
            log.tracef("Operation %d on %08X finished at %d", pair.operationId, pair.keyId, now);
         }
      }
      txModifications.clear();
      maxPrunedOperationIds.clear();
      return result;
   }

   private PrivateLogValue getNextValue(long keyId, PrivateLogValue prevValue) throws InterruptedException, BreakTxRequest {
      if (prevValue.size() >= manager.getLogLogicConfiguration().getValueMaxSize()) {
         int checkedValues;
         // TODO some limit after which the stressor will terminate
         for (; ; ) {
            if (stressor.isInterrupted() || stressor.isTerminated()) {
               return null;
            }
            long minCheckedOperation;
            try {
               minCheckedOperation = getCheckedOperation(stressor.id, prevValue.getOperationId(0));
            } catch (StressorException e) {
               return null;
            }
            /**
             * If maximum size of a log value is attained, we trim all operations, which have already been checked by log checkers.
             */
            if (prevValue.getOperationId(0) <= minCheckedOperation) {
               for (checkedValues = 1; checkedValues < prevValue.size() && prevValue.getOperationId(checkedValues) <= minCheckedOperation; ++checkedValues) {
                  log.tracef("Discarding operation %d (minimum of checked operations is %d)", prevValue.getOperationId(checkedValues), minCheckedOperation);
               }
               break;
            } else {
               try {
                  Thread.sleep(100);
               } catch (InterruptedException e) {
                  Thread.currentThread().interrupt();
                  return null;
               }
            }
         }
         if (manager.getGeneralConfiguration().getTransactionSize() > 0) {
            maxPrunedOperationIds.put(keyId, prevValue.getOperationId(checkedValues - 1));
         }
         return prevValue.shift(checkedValues, operationId);
      } else {
         return prevValue.with(operationId);
      }
   }

   private PrivateLogValue checkedGetValue(long keyId) throws Exception {
      DelayedRemove removed = delayedRemoves.get(keyId);
      if (removed != null) {
         return null;
      }
      return (PrivateLogValue) stressor.stats.startRequest().exec(BasicOperations.GET,
         () ->  basicCache.get(keyGenerator.generateKey(keyId)),
         prevValue -> {
            if (prevValue != null && !(prevValue instanceof PrivateLogValue)) {
               log.error("Value is not an instance of PrivateLogValue: " + prevValue);
               throw new IllegalStateException();
            } else {
               return true;
            }
         });
   }

   @Override
   protected boolean checkedRemoveValue(long keyId, PrivateLogValue expectedValue) throws Exception {
      Request request = stressor.stats.startRequest();
      request.exec(BasicOperations.GET_AND_REMOVE,
         // Note: with Infinspan, the returned value is sometimes unreliable anyway
         () -> basicCache.getAndRemove(keyGenerator.generateKey(keyId)),
         prevValue -> {
            if (prevValue != null) {
               if (!(prevValue instanceof PrivateLogValue)) {
                  log.errorf("Value is not an instance of PrivateLogValue: %s.", prevValue);
               } else if (!prevValue.equals(expectedValue)) {
                  log.errorf("Value is not the expected one: expected %s, found %s.", expectedValue, prevValue);
                  // As the transaction can be committed on TX coordinator & fail on other participating nodes, it can lead to
                  // exception being thrown on coordinator. For this reason an entry might not be present on the coordinator
                  // (has been removed), or its value can differ from the expected one. As this might cause false test
                  // failures, 'LogLogicConfiguration.checkDelayedRemoveExpectedValue' can be set to ignore expected value comparison.
                  if (!manager.getLogLogicConfiguration().isCheckDelayedRemoveExpectedValue()) {
                     log.trace("'LogLogicConfiguration.checkDelayedRemoveExpectedValue' set to false, ignoring check");
                     return true;
                  }
               } else {
                  return true;
               }
            } else if (expectedValue == null) {
               return true;
            } else {
               if (manager.getLogLogicConfiguration().isCheckDelayedRemoveExpectedValue()) {
                  log.errorf("Expected to remove %s but found %s.", expectedValue, prevValue);
               } else {
                  log.trace("'LogLogicConfiguration.checkDelayedRemoveExpectedValue' set to false, ignoring check");
                  return true;
               }
            }
            return false;
         });
      if (request.isSuccessful()) {
         return true;
      } else {
         throw new IllegalStateException();
      }
   }

   private void checkedPutValue(long keyId, PrivateLogValue value) throws Exception {
      stressor.stats.startRequest().exec(BasicOperations.PUT,
         () -> basicCache.put(keyGenerator.generateKey(keyId), value));
   }

   protected static class OperationTimestampPair {
      public final long operationId;
      public final long timestamp;

      public OperationTimestampPair(long operationId, long timestamp) {
         this.operationId = operationId;
         this.timestamp = timestamp;
      }
   }

   protected static class KeyOperationPair {
      public final long keyId;
      public final long operationId;

      public KeyOperationPair(long keyId, long operationId) {
         this.keyId = keyId;
         this.operationId = operationId;
      }
   }

}

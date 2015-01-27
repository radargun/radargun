package org.radargun.stages.cache.background;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

import org.radargun.Operation;
import org.radargun.stages.helpers.Range;
import org.radargun.traits.BasicOperations;

/**
 * This logic operates on {@link PrivateLogValue private log values} using only {@link BasicOperations},
 * specifically put, get and remove operations. Private log values are written to by single thread.
 *
 * @author Radim Vansa &lt;rvansa@redhat.com&gt;
 */
class PrivateLogLogic extends AbstractLogLogic<PrivateLogValue> {

   private final long keyRangeStart;
   private final long keyRangeEnd;
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

   PrivateLogLogic(BackgroundOpsManager manager, long threadId, Range range) {
      super(manager, threadId);
      log.tracef("Stressor %d has range %s", threadId, range);
      this.keyRangeStart = range.getStart();
      this.keyRangeEnd = range.getEnd();
   }

   @Override
   protected long nextKeyId() {
      return (keySelectorRandom.nextLong() & Long.MAX_VALUE) % (keyRangeEnd - keyRangeStart) + keyRangeStart;
   }

   @Override
   protected boolean invokeLogic(long keyId) throws Exception {
      Operation operation = manager.getOperation(operationTypeRandom);

      OperationTimestampPair prevOperation = timestamps.get(keyId);
      // first we have to get the value
      PrivateLogValue prevValue = checkedGetValue(keyId);
      PrivateLogValue backupValue = null;
      if (prevOperation != null) {
         if (prevValue == null || !prevValue.contains(prevOperation.operationId)) {
            // non-cleaned old value or stale read, try backup
            backupValue = checkedGetValue(~keyId);
            if (backupValue == null || !backupValue.contains(prevOperation.operationId)) {
               // definitely stale read
               waitForStaleRead(prevOperation.timestamp);
               return false;
            } else {
               // pretend that we haven't read it at all
               prevValue = null;
            }
         }
      }
      // now for modify operations, execute it
      if (prevValue == null || operation == BasicOperations.PUT) {
         PrivateLogValue nextValue;
         if (prevValue != null) {
            nextValue = getNextValue(prevValue);
         } else {
            // the value may have been removed, look for backup
            if (backupValue == null) {
               backupValue = checkedGetValue(~keyId);
            }
            if (backupValue == null) {
               nextValue = new PrivateLogValue(stressor.id, operationId);
            } else {
               nextValue = getNextValue(backupValue);
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
         PrivateLogValue nextValue = getNextValue(prevValue);
         if (nextValue == null) {
            return false;
         }
         checkedPutValue(~keyId, nextValue);
         delayedRemoveValue(keyId, prevValue);
      } else {
         // especially GETs are not allowed here, because these would break the deterministic order
         // - each operationId must be written somewhere
         throw new UnsupportedOperationException("Only PUT and REMOVE operations are allowed for this logic.");
      }

      if (transactionSize > 0) {
         txModifications.add(new KeyOperationPair(keyId, operationId));
      } else {
         long now = System.currentTimeMillis();
         timestamps.put(keyId, new OperationTimestampPair(operationId, now));
         log.tracef("Operation %d on %08X finished at %d", operationId, keyId, now);
      }
      return true;
   }

   private void waitForStaleRead(long lastWriteTimestamp) throws InterruptedException {
      long writeApplyMaxDelay = manager.getLogLogicConfiguration().writeApplyMaxDelay;
      if (writeApplyMaxDelay > 0) {
         long now = System.currentTimeMillis();
         if (lastWriteTimestamp > now - writeApplyMaxDelay){
            log.debugf("Last write of %08X was at %d, waiting 5 seconds to evade stale reads", keyId);
            Thread.sleep(5000);
         }
      } else {
         throw new RuntimeException("Stale reads are not allowed in this configuration");
      }
   }

   @Override
   protected void afterRollback() {
      super.afterRollback();
      txModifications.clear();
   }

   @Override
   protected void afterCommit() {
      super.afterCommit();
      long now = System.currentTimeMillis();
      for (KeyOperationPair pair : txModifications) {
         timestamps.put(pair.keyId, new OperationTimestampPair(pair.operationId, now));
         log.tracef("Operation %d on %08X finished at %d", pair.operationId, pair.keyId, now);
      }
      txModifications.clear();
   }

   private PrivateLogValue getNextValue(PrivateLogValue prevValue) throws InterruptedException, BreakTxRequest {
      if (prevValue.size() >= manager.getLogLogicConfiguration().getValueMaxSize()) {
         int checkedValues;
         // TODO some limit after which the stressor will terminate
         for (;;) {
            if (stressor.isInterrupted() || stressor.isTerminated()) {
               return null;
            }
            long minReadOperationId;
            try {
               minReadOperationId = getCheckedOperation(stressor.id, prevValue.getOperationId(0));
            } catch (StressorException e) {
               return null;
            }
            if (prevValue.getOperationId(0) <= minReadOperationId) {
               for (checkedValues = 1; checkedValues < prevValue.size() && prevValue.getOperationId(checkedValues) <= minReadOperationId; ++checkedValues) {
                  log.tracef("Discarding operation %d (minReadOperationId is %d)", prevValue.getOperationId(checkedValues), minReadOperationId);
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
      Object prevValue;
      long startTime = System.nanoTime();
      try {
         prevValue = basicCache.get(keyGenerator.generateKey(keyId));
      } catch (Exception e) {
         stressor.stats.registerError(System.nanoTime() - startTime, BasicOperations.GET);
         throw e;
      }
      long endTime = System.nanoTime();
      if (prevValue != null && !(prevValue instanceof PrivateLogValue)) {
         stressor.stats.registerError(endTime - startTime, BasicOperations.GET);
         log.error("Value is not an instance of PrivateLogValue: " + prevValue);
         throw new IllegalStateException();
      } else {
         stressor.stats.registerRequest(endTime - startTime, prevValue == null ? GET_NULL : BasicOperations.GET);
         return (PrivateLogValue) prevValue;
      }
   }

   @Override
   protected boolean checkedRemoveValue(long keyId, PrivateLogValue expectedValue) throws Exception {
      Object prevValue;
      long startTime = System.nanoTime();
      try {
         // Note: with Infinspan, the returned value is sometimes unreliable anyway
         prevValue = basicCache.getAndRemove(keyGenerator.generateKey(keyId));
      } catch (Exception e) {
         stressor.stats.registerError(System.nanoTime() - startTime, BasicOperations.REMOVE);
         throw e;
      }
      long endTime = System.nanoTime();
      boolean successful = false;
      if (prevValue != null) {
         if (!(prevValue instanceof PrivateLogValue)) {
            log.error("Value is not an instance of PrivateLogValue: " + prevValue);
         } else if (!prevValue.equals(expectedValue)) {
            log.error("Value is not the expected one: expected=" + expectedValue + ", found=" + prevValue);
         } else {
            successful = true;
         }
      } else if (expectedValue == null) {
         successful = true;
      } else {
         log.error("Expected to remove " + expectedValue + " but found " + prevValue);
      }
      if (successful) {
         stressor.stats.registerRequest(endTime - startTime, BasicOperations.REMOVE);
         return true;
      } else {
         stressor.stats.registerError(endTime - startTime, BasicOperations.REMOVE);
         throw new IllegalStateException();
      }
   }

   private void checkedPutValue(long keyId, PrivateLogValue value) throws Exception {
      long startTime = System.nanoTime();
      try {
         basicCache.put(keyGenerator.generateKey(keyId), value);
      } catch (Exception e) {
         stressor.stats.registerError(System.nanoTime() - startTime, BasicOperations.PUT);
         throw e;
      }
      long endTime = System.nanoTime();
      stressor.stats.registerRequest(endTime - startTime, BasicOperations.PUT);
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

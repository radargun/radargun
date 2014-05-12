package org.radargun.stages.cache.background;

import org.radargun.stages.helpers.Range;
import org.radargun.Operation;
import org.radargun.traits.BasicOperations;

/**
 * This logic operates on {@link PrivateLogValue private log values} using only {@link BasicOperations},
 * specifically put, get and remove operations. Private log values are written to by single thread.
 *
 * @author Radim Vansa &lt;rvansa@redhat.com&gt;
 */
class PrivateLogLogic extends AbstractLogLogic<PrivateLogValue> {

   private final int keyRangeStart;
   private final int keyRangeEnd;

   PrivateLogLogic(BackgroundOpsManager manager, long threadId, Range range) {
      super(manager, threadId);
      this.keyRangeStart = range.getStart();
      this.keyRangeEnd = range.getEnd();
   }

   @Override
   protected long nextKeyId() {
      return keySelectorRandom.nextInt(keyRangeEnd - keyRangeStart) + keyRangeStart;
   }

   @Override
   protected boolean invokeLogic(long keyId) throws Exception {
      Operation operation = manager.getOperation(operationTypeRandom);

      // first we have to get the value
      PrivateLogValue prevValue = checkedGetValue(keyId);
      // now for modify operations, execute it
      if (prevValue == null || operation == BasicOperations.PUT) {
         PrivateLogValue nextValue;
         PrivateLogValue backupValue = null;
         if (prevValue != null) {
            nextValue = getNextValue(prevValue);
         } else {
            // the value may have been removed, look for backup
             backupValue = checkedGetValue(~keyId);
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
      return true;
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
                  log.trace(String.format("Discarding operation %d (minReadOperationId is %d)", prevValue.getOperationId(checkedValues), minReadOperationId));
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
         stressor.stats.registerRequest(endTime - startTime, prevValue == null ? BasicOperations.GET_NULL : BasicOperations.GET);
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

}

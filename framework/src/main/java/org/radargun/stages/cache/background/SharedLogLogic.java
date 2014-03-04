package org.radargun.stages.cache.background;

import java.util.Map;

import org.radargun.stats.Operation;
import org.radargun.traits.ConditionalOperations;

/**
* @author Radim Vansa &lt;rvansa@redhat.com&gt;
*/
class SharedLogLogic extends AbstractLogLogic<SharedLogValue> {

   private final ConditionalOperations.Cache conditionalCache;
   private final long numEntries;

   SharedLogLogic(BackgroundOpsManager manager, long seed, long numEntries) {
      super(manager, seed);
      conditionalCache = manager.getConditionalCache();
      this.numEntries = numEntries;
   }

   @Override
   protected long nextKeyId() {
      return keySelectorRandom.nextLong() % numEntries;
   }

   @Override
   protected boolean invokeLogic(long keyId) throws Exception {
      Operation operation = manager.getOperation(operationTypeRandom);

      // In shared mode, we can't ever atomically modify the two keys (main and backup) to have only
      // one of them with the actual value (this is not true even for private mode but there the moment
      // when we move the value from main to backup or vice versa does not cause any problem, because the
      // worst thing is to read slightly outdated value). However, here the invariant holds that the operation
      // must be recorded in at least one of the entries, but the situation with both of these having
      // some value is valid (although, we try to evade it be conditionally removing one of them in each
      // logic step).
      SharedLogValue prevValue, backupValue, nextValue;
      do {
         prevValue = checkedGetValue(keyId);
         backupValue = checkedGetValue(~keyId);
         nextValue = getNextValue(prevValue, backupValue);
         if (stressor.isTerminated() || stressor.isInterrupted()) return false;
      } while (nextValue == null);
      // now for modify operations, execute it
      if (operation == Operation.PUT) {
         if (checkedPutValue(keyId, prevValue, nextValue)) {
            if (backupValue != null) {
               delayedRemoveValue(~keyId, backupValue);
            }
         } else {
            return false;
         }
      } else if (operation == Operation.REMOVE) {
         if (checkedPutValue(~keyId, backupValue, nextValue)) {
            if (prevValue != null) {
               delayedRemoveValue(keyId, prevValue);
            }
         } else {
            return false;
         }
      } else {
         // especially GETs are not allowed here, because these would break the deterministic order
         // - each operationId must be written somewhere
         throw new UnsupportedOperationException("Only PUT and REMOVE operations are allowed for this logic.");
      }
      return true;
   }

   private SharedLogValue getNextValue(SharedLogValue prevValue, SharedLogValue backupValue) throws StressorException, BreakTxRequest {
      if (prevValue == null && backupValue == null) {
         return new SharedLogValue(stressor.id, operationId);
      } else if (prevValue != null && backupValue != null) {
         SharedLogValue joinValue = prevValue.join(backupValue);
         if (joinValue.size() >= manager.getLogValueMaxSize()) {
            return filterAndAddOperation(joinValue);
         } else {
            return joinValue.with(stressor.id, operationId);
         }
      }
      SharedLogValue value = prevValue != null ? prevValue : backupValue;
      if (value.size() < manager.getLogValueMaxSize()) {
         return value.with(stressor.id, operationId);
      } else {
         return filterAndAddOperation(value);
      }
   }

   private SharedLogValue filterAndAddOperation(SharedLogValue value) throws StressorException, BreakTxRequest {
      Map<Integer, Long> operationIds = getCheckedOperations(value.minFrom(stressor.id));
      SharedLogValue filtered = value.with(stressor.id, operationId, operationIds);
      if (filtered.size() > manager.getLogValueMaxSize()) {
         return null;
      } else {
         return filtered;
      }
   }

   private SharedLogValue checkedGetValue(long keyId) throws Exception {
      Object prevValue;
      long startTime = System.nanoTime();
      try {
         prevValue = basicCache.get(keyGenerator.generateKey(keyId));
      } catch (Exception e) {
         stressor.stats.registerError(System.nanoTime() - startTime, 0, Operation.GET);
         throw e;
      }
      long endTime = System.nanoTime();
      if (prevValue != null && !(prevValue instanceof SharedLogValue)) {
         stressor.stats.registerError(endTime - startTime, 0, Operation.GET);
         log.error("Value is not an instance of SharedLogValue: " + prevValue);
         throw new IllegalStateException();
      } else {
         stressor.stats.registerRequest(endTime - startTime, 0, prevValue == null ? Operation.GET_NULL : Operation.GET);
         return (SharedLogValue) prevValue;
      }
   }

   private boolean checkedPutValue(long keyId, SharedLogValue oldValue, SharedLogValue newValue) throws Exception {
      boolean returnValue;
      long startTime = System.nanoTime();
      try {
         if (oldValue == null) {
            returnValue = conditionalCache.putIfAbsent(keyGenerator.generateKey(keyId), newValue);
         } else {
            returnValue = conditionalCache.replace(keyGenerator.generateKey(keyId), oldValue, newValue);
         }
      } catch (Exception e) {
         stressor.stats.registerError(System.nanoTime() - startTime, 0, Operation.PUT);
         throw e;
      }
      long endTime = System.nanoTime();
      stressor.stats.registerRequest(endTime - startTime, 0, Operation.PUT);
      return returnValue;
   }

   @Override
   protected boolean checkedRemoveValue(long keyId, SharedLogValue oldValue) throws Exception {
      long startTime = System.nanoTime();
      try {
         boolean returnValue = conditionalCache.remove(keyGenerator.generateKey(keyId), oldValue);
         long endTime = System.nanoTime();
         stressor.stats.registerRequest(endTime - startTime, 0, Operation.REMOVE);
         return returnValue;
      } catch (Exception e) {
         stressor.stats.registerError(System.nanoTime() - startTime, 0, Operation.REMOVE);
         throw e;
      }
   }
}

package org.radargun.stages.cache.background;

import java.util.HashMap;
import java.util.Map;

import org.radargun.Operation;
import org.radargun.stages.helpers.Range;
import org.radargun.stats.Request;
import org.radargun.traits.BasicOperations;
import org.radargun.traits.ConditionalOperations;

/**
 * This logic operates on {@link SharedLogValue shared log values}
 * and requires {@link ConditionalOperations} on the cache.
 * With this setup, multiple stressors can change one log value concurrently.
 *
 * @author Radim Vansa &lt;rvansa@redhat.com&gt;
 */
class SharedLogLogic extends AbstractLogLogic<SharedLogValue> {

   private final ConditionalOperations.Cache nonTxConditionalCache;
   private ConditionalOperations.Cache conditionalCache;

   SharedLogLogic(BackgroundOpsManager manager, Range range) {
      super(manager, range);
      nonTxConditionalCache = manager.getConditionalCache();
      if (transactionSize <= 0) {
         conditionalCache = nonTxConditionalCache;
      }
   }

   @Override
   protected boolean invokeLogic(long keyId) throws Exception {
      Operation operation = getOperation(operationTypeRandom);

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
         if (stressor.isTerminated() || stressor.isInterrupted()) {
            return false;
         }
      } while (nextValue == null);
      // now for modify operations, execute it
      if (operation == BasicOperations.PUT) {
         if (checkedPutValue(keyId, prevValue, nextValue)) {
            if (backupValue != null) {
               delayedRemoveValue(~keyId, backupValue);
            }
         } else {
            return false;
         }
      } else if (operation == BasicOperations.REMOVE) {
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
         if (joinValue.size() >= manager.getLogLogicConfiguration().getValueMaxSize()) {
            return filterAndAddOperation(joinValue);
         } else {
            return joinValue.with(stressor.id, operationId);
         }
      }
      SharedLogValue value = prevValue != null ? prevValue : backupValue;
      if (value.size() < manager.getLogLogicConfiguration().getValueMaxSize()) {
         return value.with(stressor.id, operationId);
      } else {
         return filterAndAddOperation(value);
      }
   }

   private SharedLogValue filterAndAddOperation(SharedLogValue value) throws StressorException, BreakTxRequest {
      Map<Integer, Long> operationIds = getCheckedOperations(value.minFrom(stressor.id));
      SharedLogValue filtered = value.with(stressor.id, operationId, operationIds);
      if (filtered.size() > manager.getLogLogicConfiguration().getValueMaxSize()) {
         return null;
      } else {
         return filtered;
      }
   }

   protected Map<Integer, Long> getCheckedOperations(long operationId) throws StressorException, BreakTxRequest {
      Map<Integer, Long> minCheckedOperations = new HashMap<>();
      for (int stressorId = 0; stressorId < manager.getGeneralConfiguration().getNumThreads() * manager.getWorkerState().getGroupSize(); ++stressorId) {
         minCheckedOperations.put(stressorId, getCheckedOperation(stressorId, operationId));
      }
      return minCheckedOperations;
   }

   @Override
   protected void startTransaction() {
      ongoingTx = manager.newTransaction();
      basicCache = ongoingTx.wrap(nonTxBasicCache);
      conditionalCache = ongoingTx.wrap(nonTxConditionalCache);
      ongoingTx.begin();
   }

   @Override
   protected void clearTransaction() {
      super.clearTransaction();
      conditionalCache = null;
   }

   private SharedLogValue checkedGetValue(long keyId) throws Exception {
      return (SharedLogValue) stressor.stats.startRequest().exec(BasicOperations.GET,
         () -> basicCache.get(keyGenerator.generateKey(keyId)),
         prevValue -> {
            if (prevValue != null && !(prevValue instanceof SharedLogValue)) {
               log.error("Value is not an instance of SharedLogValue: " + prevValue);
               throw new IllegalStateException();
            }
            return true;
         });
   }

   private boolean checkedPutValue(long keyId, SharedLogValue oldValue, SharedLogValue newValue) throws Exception {
      Request request = stressor.stats.startRequest();
      if (oldValue == null) {
         return request.exec(ConditionalOperations.PUT_IF_ABSENT,
            () -> conditionalCache.putIfAbsent(keyGenerator.generateKey(keyId), newValue));
      } else {
         return request.exec(ConditionalOperations.REPLACE,
            () -> conditionalCache.replace(keyGenerator.generateKey(keyId), oldValue, newValue));
      }
   }

   @Override
   protected boolean checkedRemoveValue(long keyId, SharedLogValue oldValue) throws Exception {
      return stressor.stats.startRequest().exec(ConditionalOperations.REMOVE,
         () -> conditionalCache.remove(keyGenerator.generateKey(keyId), oldValue));
   }
}

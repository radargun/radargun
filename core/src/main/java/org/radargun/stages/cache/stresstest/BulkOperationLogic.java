package org.radargun.stages.cache.stresstest;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;

import org.radargun.Operation;
import org.radargun.traits.BulkOperations;

/**
 * Executes bulk operations (operations involving multiple keys) against the cache.
 *
 * @author Radim Vansa &lt;rvansa@redhat.com&gt;
 */
class BulkOperationLogic implements OperationLogic {
   private final FixedSetOperationLogic initLogic;
   private final Operation putOperation;
   private final Operation removeOperation;
   private final Operation getOperation;
   private StressTestStage stage;

   public BulkOperationLogic(StressTestStage stage, FixedSetOperationLogic initLogic, boolean preferAsyncOperations) {
      this.stage = stage;
      this.initLogic = initLogic;
      if (preferAsyncOperations) {
         putOperation = BulkOperations.PUT_ALL_ASYNC;
         removeOperation = BulkOperations.REMOVE_ALL_ASYNC;
         getOperation = BulkOperations.GET_ALL_ASYNC;
      } else {
         putOperation = BulkOperations.PUT_ALL_NATIVE;
         removeOperation = BulkOperations.REMOVE_ALL_NATIVE;
         getOperation = BulkOperations.GET_ALL_NATIVE;
      }
   }

   @Override
   public void init(Stressor stressor) {
      initLogic.init(stressor);
   }

   @Override
   public Object run(Stressor stressor) throws RequestException {
      Random r = ThreadLocalRandom.current();
      int randomAction = r.nextInt(100);
      if (randomAction < stage.writePercentage) {
         Map<Object, Object> map = new HashMap<Object, Object>(stage.bulkSize);
         for (int i = 0; i < stage.bulkSize;) {
            Object key = initLogic.getKey(r.nextInt(stage.numEntries - 1), stressor.getThreadIndex());
            if (!map.containsKey(key)) {
               map.put(key, stage.generateValue(key, Integer.MAX_VALUE, stressor.getRandom()));
               ++i;
            }
         }
         return stressor.makeRequest(putOperation, map);
      } else {
         Set<Object> set = new HashSet<Object>(stage.bulkSize);
         for (int i = 0; i < stage.bulkSize; ) {
            Object key = initLogic.getKey(r.nextInt(stage.numEntries - 1), stressor.getThreadIndex());
            if (!set.contains(key)) {
               set.add(key);
               ++i;
            }
         }
         if (randomAction < stage.writePercentage + stage.removePercentage) {
            return stressor.makeRequest(removeOperation, set);
         } else {
            return stressor.makeRequest(getOperation, set);
         }
      }
   }
}

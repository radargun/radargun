package org.radargun.stages.cache.stresstest;

import java.util.HashMap;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.ThreadLocalRandom;

import org.radargun.stats.Operation;

/**
* @author Radim Vansa &lt;rvansa@redhat.com&gt;
*/
class FixedSetConditionalOperationLogic extends FixedSetPerThreadOperationLogic {
   private Map<Object, Object> lastValues = new HashMap<Object, Object>(stage.numEntries);

   public FixedSetConditionalOperationLogic(StressTestStage stage) {
      super(stage);
   }

   @Override
   public Object run(Stressor stressor) throws RequestException {
      Random r = ThreadLocalRandom.current();
      int randomAction = r.nextInt(100);
      Object key = getKey(r.nextInt(stage.numEntries - 1), stressor.getThreadIndex());
      Object lastValue = lastValues.get(key);

      Object newValue = stage.generateValue(key, Integer.MAX_VALUE);
      int probability = 0;
      if (lastValue == null) {
         lastValues.put(key, newValue);
         return stressor.makeRequest(Operation.PUT_IF_ABSENT_IS_ABSENT, key, newValue);
      } else if (randomAction < (probability += stage.writePercentage)) {
         return stressor.makeRequest(Operation.PUT_IF_ABSENT_NOT_ABSENT, key, newValue, lastValue);
      } else if (randomAction < (probability += stage.removePercentage)) {
         lastValues.remove(key);
         return stressor.makeRequest(Operation.REMOVE_VALID, key, lastValue);
      } else if (randomAction < (probability += stage.removeInvalidPercentage)) {
         return stressor.makeRequest(Operation.REMOVE_INVALID, key, stage.generateValue(key, Integer.MAX_VALUE));
      } else if (randomAction < (probability += stage.replaceInvalidPercentage)) {
         return stressor.makeRequest(Operation.REPLACE_INVALID, key, stage.generateValue(key, Integer.MAX_VALUE), newValue);
      } else {
         lastValues.put(key, newValue);
         return stressor.makeRequest(Operation.REPLACE_VALID, key, lastValue, newValue);
      }
   }

   @Override
   protected void addPooledKey(Object key, Object value) {
      super.addPooledKey(key, value);
      lastValues.put(key, value);
   }
}

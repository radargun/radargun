package org.radargun.stages.cache.stresstest;

import java.util.HashMap;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.ThreadLocalRandom;

import org.radargun.traits.ConditionalOperations;

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
         return stressor.makeRequest(ConditionalOperations.PUT_IF_ABSENT_EXEC, key, newValue);
      } else if (randomAction < (probability += stage.writePercentage)) {
         return stressor.makeRequest(ConditionalOperations.PUT_IF_ABSENT_NOTEX, key, newValue, lastValue);
      } else if (randomAction < (probability += stage.removePercentage)) {
         lastValues.remove(key);
         return stressor.makeRequest(ConditionalOperations.REMOVE_EXEC, key, lastValue);
      } else if (randomAction < (probability += stage.removeInvalidPercentage)) {
         return stressor.makeRequest(ConditionalOperations.REMOVE_NOTEX, key, stage.generateValue(key, Integer.MAX_VALUE));
      } else if (randomAction < (probability += stage.replaceInvalidPercentage)) {
         return stressor.makeRequest(ConditionalOperations.REPLACE_NOTEX, key, stage.generateValue(key, Integer.MAX_VALUE), newValue);
      } else {
         lastValues.put(key, newValue);
         return stressor.makeRequest(ConditionalOperations.REPLACE_EXEC, key, lastValue, newValue);
      }
   }

   @Override
   protected void addPooledKey(Object key, Object value) {
      super.addPooledKey(key, value);
      lastValues.put(key, value);
   }
}

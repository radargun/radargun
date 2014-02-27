package org.radargun.stages.cache.stresstest;

import java.util.Random;
import java.util.concurrent.ThreadLocalRandom;

import org.radargun.stats.Operation;

/**
* @author Radim Vansa &lt;rvansa@redhat.com&gt;
*/
abstract class FixedSetOperationLogic implements OperationLogic {
   protected StressTestStage stage;

   public FixedSetOperationLogic(StressTestStage stage) {
      this.stage = stage;
   }

   @Override
   public Object run(Stressor stressor) throws RequestException {
      Random r = ThreadLocalRandom.current();
      int randomAction = r.nextInt(100);
      int randomKeyInt = r.nextInt(stage.numEntries - 1);
      Object key = getKey(randomKeyInt, stressor.getThreadIndex());

      if (randomAction < stage.writePercentage) {
         return stressor.makeRequest(Operation.PUT, key, stage.generateValue(key, Integer.MAX_VALUE));
      } else if (randomAction < stage.writePercentage + stage.removePercentage) {
         return stressor.makeRequest(Operation.REMOVE, key);
      } else {
         return stressor.makeRequest(Operation.GET, key);
      }
   }

   protected abstract Object getKey(int keyId, int threadIndex);
}

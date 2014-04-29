package org.radargun.stages.cache.stresstest;

import java.util.Random;
import java.util.concurrent.ThreadLocalRandom;

import org.radargun.traits.BasicOperations;

/**
 * Logic which executes the operations on fixed set of keys, given by
 * key IDs 0 - {@link StressTestStage#numEntries}
 *
 * This logic executes operations:
 * - {@link BasicOperations.Cache#put(Object, Object) PUT}
 * - {@link BasicOperations.Cache#remove(Object) REMOVE}
 * - {@link BasicOperations.Cache#get(Object) GET}
 * with probability given by {@link StressTestStage#writePercentage writePercentage}
 * and {@link StressTestStage#removePercentage removePercentage}.
 *
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
         return stressor.makeRequest(BasicOperations.PUT, key, stage.generateValue(key, Integer.MAX_VALUE));
      } else if (randomAction < stage.writePercentage + stage.removePercentage) {
         return stressor.makeRequest(BasicOperations.REMOVE, key);
      } else {
         return stressor.makeRequest(BasicOperations.GET, key);
      }
   }

   /**
    * Retrieve key with given key ID (this is not the keyId passed to
    * {@link org.radargun.stages.cache.generators.KeyGenerator#generateKey(long)})
    * for thread with specified index.
    */
   protected abstract Object getKey(int keyId, int threadIndex);
}

package org.radargun.stages.cache.stresstest;

import java.util.ArrayList;
import java.util.concurrent.atomic.AtomicLong;

import org.radargun.stages.cache.generators.KeyGenerator;
import org.radargun.logging.Log;
import org.radargun.logging.LogFactory;
import org.radargun.traits.BasicOperations;

/**
 * The sets of keys for the stressor threads are distinct when using this logic.
 *
 * All keys are loaded to the cache in the {@link OperationLogic#init(Stressor) init} method, prior to test execution.
 *
 * @author Radim Vansa &lt;rvansa@redhat.com&gt;
 */
class FixedSetPerThreadOperationLogic extends FixedSetOperationLogic {
   private static Log log = LogFactory.getLog(StressTestStage.class);

   private ArrayList<Object> pooledKeys;
   private int myLoadedKeys = 0;
   private AtomicLong keysLoaded = new AtomicLong(0);
   private int nodeIndex;

   public FixedSetPerThreadOperationLogic(StressTestStage stage) {
      super(stage);
      if (stage.poolKeys) {
         this.pooledKeys = new ArrayList<Object>(stage.numEntries);
      }
   }

   @Override
   public void init(Stressor stressor) {
      this.nodeIndex = stressor.getNodeIndex();
      if (stage.poolKeys) {
         if (pooledKeys.size() == stage.numEntries) return;
      } else {
         if (myLoadedKeys == stage.numEntries) return;
      }
      BasicOperations.Cache cache = stage.basicOperations.getCache(stage.bucketPolicy.getBucketName(stressor.getThreadIndex()));
      KeyGenerator keyGenerator = stage.getKeyGenerator();
      for (int keyIndex = 0; keyIndex < stage.numEntries; keyIndex++) {
         Object key = keyGenerator.generateKey((nodeIndex * stage.numThreads + stressor.getThreadIndex()) * stage.numEntries + keyIndex);
         Object value = stage.generateValue(key, Integer.MAX_VALUE, stressor.getRandom());
         addPooledKey(key, value);
         for (int i = 0; i < stage.maxLoadAttempts; ++i) {
            try {
               cache.put(key, value);
               long loaded = keysLoaded.incrementAndGet();
               if (loaded % stage.logPeriod == 0) {
                  Runtime runtime = Runtime.getRuntime();
                  log.info(String.format("Loaded %d/%d entries (on this node), free %d MB/%d MB",
                        loaded, stage.numEntries * stage.numThreads, runtime.freeMemory() / 1048576, runtime.maxMemory() / 1048576));
               }
               break;
            } catch (Throwable e) {
               log.warn("Failed to insert key " + key, e);
            }
         }
      }
   }

   protected void addPooledKey(Object key, Object value) {
      if (stage.poolKeys) {
         pooledKeys.add(key);
      } else {
         myLoadedKeys++;
      }
   }

   protected Object getKey(int keyId, int threadIndex) {
      if (stage.poolKeys) {
         return pooledKeys.get(keyId);
      } else {
         return stage.keyGenerator.generateKey((nodeIndex * stage.numThreads + threadIndex) * stage.numEntries + keyId);
      }
   }
}

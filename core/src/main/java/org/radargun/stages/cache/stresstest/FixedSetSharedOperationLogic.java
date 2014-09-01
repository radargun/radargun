package org.radargun.stages.cache.stresstest;

import java.util.ArrayList;
import java.util.concurrent.atomic.AtomicLong;

import org.radargun.stages.cache.generators.KeyGenerator;
import org.radargun.logging.Log;
import org.radargun.logging.LogFactory;
import org.radargun.traits.BasicOperations;

/**
 * All threads execute the operations on shared set of keys.
 * Therefore, locking conflicts can happen in the underlying cache.
 *
 * @author Radim Vansa &lt;rvansa@redhat.com&gt;
 */
class FixedSetSharedOperationLogic extends FixedSetOperationLogic {
   private static Log log = LogFactory.getLog(StressTestStage.class);

   private ArrayList<Object> sharedKeys;
   private AtomicLong keysLoaded = new AtomicLong(0);

   public FixedSetSharedOperationLogic(StressTestStage stage, ArrayList<Object> sharedKeys) {
      super(stage);
      this.sharedKeys = sharedKeys;
   }

   @Override
   public Object getKey(int keyId, int threadIndex) {
      if (stage.poolKeys) {
         return sharedKeys.get(keyId);
      } else {
         return stage.keyGenerator.generateKey(keyId);
      }
   }

   @Override
   public void init(Stressor stressor) {
      if (stage.poolKeys) {
         synchronized (sharedKeys) {
            // no point in doing this in parallel, too much overhead in synchronization
            if (stressor.getThreadIndex() == 0) {
               if (sharedKeys.size() != stage.numEntries) {
                  sharedKeys.clear();
                  KeyGenerator keyGenerator = stage.getKeyGenerator();
                  for (int keyIndex = 0; keyIndex < stage.numEntries; ++keyIndex) {
                     sharedKeys.add(keyGenerator.generateKey(keyIndex));
                  }
               }
               sharedKeys.notifyAll();
            } else {
               while (sharedKeys.size() != stage.numEntries) {
                  try {
                     sharedKeys.wait();
                  } catch (InterruptedException e) {
                  }
               }
            }
         }
      }
      int loadedEntryCount, keyIndex, loadingThreads;
      if (stage.loadAllKeys) {
         loadedEntryCount = stage.numEntries;
         loadingThreads = stage.numThreads;
         keyIndex = stressor.getThreadIndex();
      } else {
         loadedEntryCount = stage.numEntries / stressor.getNumNodes()
               + (stressor.getNodeIndex() < stage.numEntries % stressor.getNumNodes() ? 1 : 0);
         loadingThreads = stage.numThreads * stressor.getNumNodes();
         keyIndex = stressor.getThreadIndex() + stressor.getNodeIndex() * stage.numThreads;
      }
      if (stressor.getThreadIndex() == 0) {
         log.info(String.format("We have loaded %d keys, expecting %d locally loaded",
               keysLoaded.get(), loadedEntryCount));
      }
      if (keysLoaded.get() >= loadedEntryCount) {
         return;
      }
      BasicOperations.Cache cache = stage.basicOperations.getCache(stage.bucketPolicy.getBucketName(stressor.getThreadIndex()));
      for (; keyIndex < stage.numEntries; keyIndex += loadingThreads) {
         for (int i = 0; i < stage.maxLoadAttempts; ++i) {
            try {
               Object key = getKey(keyIndex, stressor.getThreadIndex());
               cache.put(key, stage.generateValue(key, Integer.MAX_VALUE, stressor.getRandom()));
               long loaded = keysLoaded.incrementAndGet();
               if (loaded % stage.logPeriod == 0) {
                  Runtime runtime = Runtime.getRuntime();
                  log.info(String.format("Loaded %d/%d entries (on this node), free %d MB/%d MB",
                        loaded, loadedEntryCount, runtime.freeMemory() / 1048576, runtime.maxMemory() / 1048576));
               }
               break;
            } catch (Exception e) {
               log.error("Failed to insert shared key " + keyIndex, e);
            }
         }
      }
      // make sure all stressors init their keys before making requests to avoid null return values.
      if (stage.sharedKeys && !stage.loadAllKeys) {
         // key_set_init_ack_${nodeIndex}_${threadIndex}
         final String ackKeyTemplate = "key_set_init_ack_%s_%s";
         cache.put(String.format(ackKeyTemplate, stressor.getNodeIndex(), stressor.getThreadIndex()), "ack_key");
         log.trace(String.format("Keys have been initialized for thread %s on node %s.", stressor.getThreadIndex(), stressor.getNodeIndex()));
         boolean keysInitialized = false;
         outer : while (!keysInitialized) {
            for (int node = 0; node < stressor.getNumNodes(); node++) {
               for (int thread = 0; thread < stage.numThreads; thread++) {
                  if (!cache.containsKey(String.format(ackKeyTemplate, node, thread))) {
                     try {
                        Thread.sleep(100);
                     } catch (InterruptedException e) {
                        log.error(String.format("Exception while suspending thread %s on node %s", stressor.getThreadIndex(), stressor.getNodeIndex()), e);
                     } finally {
                        continue outer;
                     }
                  }
               }
            }
            keysInitialized = true;
         }
      }
   }
}
